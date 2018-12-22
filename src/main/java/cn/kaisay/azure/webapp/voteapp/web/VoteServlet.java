package cn.kaisay.azure.webapp.voteapp.web;




import cn.kaisay.azure.webapp.voteapp.biz.BizTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;


@WebServlet(asyncSupported = true, value = "/vote")
public class VoteServlet extends HttpServlet {

    private static Logger logger = LogManager.getLogger();

    private boolean healthy = true;

    private final String SERVER_HEALTHY_FLAG = "healthy";

    /**
     * 最大每次100个线程同时处理
     */
    private static int jobSize = 100;

    /**
     * 每s最大批处理量
     */
    private static int times = 20;

    private long  timeout = 3000;

    private int quequeSize = 20000;

    private int slowQuequeSize = 5000;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private ExecutorService executorSlowLoopService = Executors.newSingleThreadExecutor();

    private ScheduledExecutorService monitorScheduler = Executors.newScheduledThreadPool(1);

    private static final BlockingQueue<AsyncContext> queue = new ArrayBlockingQueue<>(20000);

    private static final BlockingQueue<BizTask> slowQueue = new ArrayBlockingQueue<>(500);

    private static final String javaVersion =
            "java.specification.version"+System.getProperty("java.specification.version")
            +"java.specification.vendor"+System.getProperty("java.specification.vendor")
            +"java.specification.name"+System.getProperty("java.specification.name");

    public boolean isHealthy() {
        return healthy;
    }

    private synchronized void  setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws  IOException {
        if(!isHealthy()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.addHeader(SERVER_HEALTHY_FLAG,"status: no. the queue size is: "+queue.size()
                    +"; the slow queue is: "+slowQueue.size());
            return;
        }
        addToWaitingList(request.startAsync());
    }



    private long maxTime() {
        return 1000/(times>0?times:1);
    }

    /**
     * 动态调整服务器的处理能力
     * @param request
     * @param response
     * @throws ServletException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws  IOException {
        try {
            jobSize = Integer.parseInt(request.getParameter("jobSize"));
        } catch (Exception e) {
            jobSize = 10;
        }

        try {
            times = Integer.parseInt(request.getParameter("times"));
        } catch (Exception e) {
            times = 100;
        }

        logger.debug("Set the jobsize to "+jobSize+" and times to "+times+"queue now is "+queue.size());
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("Set the jobsize to "+jobSize+" and times to "+times+" queue now is "+queue.size());

    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp)  {
//        resp.addHeader("java runtime",javaVersion);
        if(!isHealthy()) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.addHeader(SERVER_HEALTHY_FLAG,"status:no current queue size is "+queue.size());
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.addHeader(SERVER_HEALTHY_FLAG,"status:ok current queue size is "+queue.size());
        }

    }


    {
        executorService.submit(()->acceptLoop());
        executorSlowLoopService.submit(()->slowLoop());
        monitorScheduler.scheduleAtFixedRate(()->{
            if (queue.size() != quequeSize && slowQueue.size() != slowQuequeSize) {
                setHealthy(true);
            }
        },0,1,TimeUnit.MINUTES);
    }


    private void slowLoop() {
        while(true) {
            ArrayList<BizTask> clients = new ArrayList<>();
            try {
                clients.add(slowQueue.take());
                slowQueue.drainTo(clients,jobSize);
                clients.forEach(task->{
                    CompletableFuture.runAsync(()->{
                        logger.info(()->"[slow] starting to get the vote data from the request and persistence it.");
                        task.going();
                    }).orTimeout(task.timeLeft().toMillis(), TimeUnit.MILLISECONDS).whenComplete(
                            (v,e)->{
                                if (e == null) {
                                    task.slowOk();
                                } else if(e instanceof TimeoutException) {
                                    task.timeout();
                                } else {
                                    logger.error(()->"error when processing slowQueue...");
                                    e.printStackTrace();
                                    task.error();
                                }
                            }
                    );
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }

    /**
     * 每s的总处理能力上限为jobSize * times ，因为服务器的硬件处理能力有限，需要限制此数值大小
     */
    private void acceptLoop() {
        while(true) {
            try {
                ArrayList<AsyncContext> clients = new ArrayList<>();
                clients.add(queue.take());
                //通过jobSize 和 times 控制从request等待队列处理的速度最大每s处理jobSize
                queue.drainTo(clients, jobSize);
                clients.forEach(ac->{
                    //当业务处理超时，则转交进入slowQueue
                    BizTask task = new BizTask(ac);
                    CompletableFuture.runAsync(() -> {
                                logger.info(()->"[1] starting to get the vote data from the request and persistence it.");
                                task.processing();
                            })
                            .orTimeout(3,TimeUnit.SECONDS)
                            .whenComplete((v,error)->{
                                if(error == null) {
                                    task.ok();
                                } else if (error instanceof TimeoutException) {
                                    try {
                                        logger.warn(()->"request processing is slow, adding to the slowQueue.");
                                        slowQueue.add(task);
                                    } catch (Exception e) {
                                        logger.error(()->"error when moving to the slowQueue...");
                                        e.printStackTrace();
                                        this.setHealthy(false);
                                        task.unavailable();
                                    }
                                } else {
                                    logger.error(()->"error when accept queue");
                                    error.printStackTrace();
                                    task.error();
                                }
                            });

                });
                Thread.sleep(1000/(times>0?times:1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void addToWaitingList(AsyncContext c) throws IOException {
        try {
            queue.add(c);
            setHealthy(true);
        } catch (IllegalStateException ie) {
            logger.error(()->"Exceed Server Capacity");
            this.setHealthy(false);
            // 如果加入队列失败，则返回capcity 异常
            ((HttpServletResponse)c.getResponse()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            c.getResponse().getWriter().write("Exceed Server Capacity.");
            c.complete();

        } catch (Exception e) {
            logger.error(()->"Server Error");
            ((HttpServletResponse)c.getResponse()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            c.getResponse().getWriter().write("Internal_server_error");
            c.complete();

        }
    }
}
