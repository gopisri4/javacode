package cn.kaisay.azure.webapp.voteapp.web;


import cn.kaisay.azure.webapp.voteapp.biz.BizTask;
import cn.kaisay.azure.webapp.voteapp.model.Setup;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@WebServlet(asyncSupported = true, value = "/vote/*")
public class VoteServlet extends HttpServlet {

    private static final BlockingQueue<BizTask> queue = new ArrayBlockingQueue<>(20000);
//    private static final BlockingQueue<BizTask> slowQueue = new ArrayBlockingQueue<>(500);
    private static final String javaVersion =
            "java.specification.version" + System.getProperty("java.specification.version")
                    + " java.specification.vendor=> " + System.getProperty("java.specification.vendor")
                    + " java.specification.name=> " + System.getProperty("java.specification.name");
    private static final int processors = Runtime.getRuntime().availableProcessors() == 0
            ? 1 : Runtime.getRuntime().availableProcessors();
    /**
     * 最大每次同时处理500*processor numbers 个job
     */
    private static final int maxJobSize = 500 * processors;
    private static final int minJobSize = 100;
    /**
     * 每s最大批处理量和最小批处理量
     */
    private static final int minTimes = 1;
    private static final int maxTimes = 250;
    private static Logger logger = LogManager.getLogger();
    private static volatile int jobSize = 50 * processors;
    private static volatile int times = 20;
    private final String SERVER_HEALTHY_FLAG = "healthy";
    private boolean healthy = true;
    private int maxQuequeSize = 20000;
    private int maxSlowQuequeSize = 200;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
//    private ExecutorService executorSlowLoopService = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService monitorScheduler = Executors.newScheduledThreadPool(1);
    public static ScheduledExecutorService slowMonitorScheduler = Executors.newScheduledThreadPool(1);
    public static AtomicInteger slowNumber = new AtomicInteger(0);

    {
        executorService.submit(() -> acceptLoop());
//        executorSlowLoopService.submit(() -> slowLoop());
        monitorScheduler.scheduleAtFixedRate(() -> {
            if (queue.size() != maxQuequeSize && slowNumber.get() != maxSlowQuequeSize) {
                setHealthy(true);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    public boolean isHealthy() {
        return healthy;
    }

    private synchronized void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isHealthy()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.addHeader(SERVER_HEALTHY_FLAG, "status: no. the queue size is: " + queue.size()
                    + "; the slow queue is: " + slowNumber.get());
            return;
        }
        addToWaitingList(request.startAsync());
    }

//    private long maxTime() {
//        return 1000 / (times > 0 ? times : 1);
//    }

    /**
     * 动态调整服务器的处理能力
     * the path like /vote/setup/jobSize/10/times/100
     * @param request
     * @param response
     * @throws ServletException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<String> pathInfo = Optional.ofNullable(request.getPathInfo());
        pathInfo.ifPresentOrElse(path -> {
            logger.debug(() -> "there's an extra path string existing.." + path);
            //TODO get the parameters from path to setup the jobsize the job frequency
            List<String> args = Arrays.asList(path.split("/"))
                    .stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
            logger.debug("the arg list is " + args);
            if (args.isEmpty()) {
                doDefault(request, response);
                return;
            }
            switch (args.get(0)) {
                case "setup":
                    doSetup(args.subList(1, args.size()), request, response);
                    break;
                case "random":
                    doRandom(args.subList(1, args.size()), request, response);
                    break;
                default:
                    doDefault(request, response);
            }
        }, () -> {
            logger.debug(() -> "there's no extra path string existing..");
            doDefault(request, response);
        });

    }

    /**
     * 配置mock任务的处理时间，随机处理
     * @param subList
     * @param request
     * @param response
     */
    private void doRandom(List<String> subList, HttpServletRequest request, HttpServletResponse response) {
        //TODO to config the pcocessing time boundary
        doDefault(request,response);
    }

    private synchronized void doSetup(List<String> args, HttpServletRequest request, HttpServletResponse response) {
        Setup setup = new Setup();
        try {
            if (validate(args, setup)) {
                jobSize = setup.getJobSize().orElse(jobSize);
                times = setup.getTimes().orElse(times);
            }
            doDefault(request, response);
        } catch (Exception e) {
            logger.warn("setup action is wrong, the args is " + args);
            doDefaultError(e.getMessage() +
                    " the setup action restPath should be like " +
                    "/setup/job/x/times/y x's range is [100,100*processor number] and y's range is [10,500]", request, response);
        }


    }

    private void doDefaultError(String message, HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("message", message);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.addHeader("message", message);
        doDefault(request, response);
    }

    private boolean validate(List<String> args, Setup setup) {
        if (args == null || args.size() == 0 || args.size() % 2 != 0) {
            throw new RuntimeException("the arg format is not right");
        }
        if (!args.get(0).matches("^[Jj]([oO][Bb])?$")) {
            throw new RuntimeException("the first arg should be \"job\", it's case insensitive");
        }

        if (!args.get(2).matches("^[Tt]([Ii][Mm][Ee])?$")) {
            throw new RuntimeException("the second arg should be \"time\", it's case insensitive");
        }

        int jobsize = Integer.parseInt(args.get(1));
        jobsize = Integer.max(minJobSize, jobsize);
        jobsize = Integer.min(maxJobSize, jobsize);
        int times = Integer.parseInt(args.get(3));
        times = Integer.max(minTimes, times);
        times = Integer.min(maxTimes, times);

        setup.setJobSize(Optional.of(jobsize));
        setup.setTimes(Optional.of(times));

        return true;
    }

    private void doDefault(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("jobSize", jobSize);
        request.setAttribute("times", times);
        request.setAttribute("queueSize", queue.size());
        request.setAttribute("slowQueueSize", slowNumber.get());
        request.setAttribute("jvm", javaVersion);
        try {
            getServletContext()
                    .getRequestDispatcher("/status.jsp")
                    .forward(request, response);
        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) {
        resp.addHeader("java runtime", javaVersion);
        if (!isHealthy()) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.addHeader(SERVER_HEALTHY_FLAG, "status:no current queue size is " + queue.size());
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.addHeader(SERVER_HEALTHY_FLAG, "status:ok current queue size is " + queue.size());
        }

    }

    /**
     * 每s的总处理能力上限为jobSize * times ，因为服务器的硬件处理能力有限，需要限制此数值大小
     * 通过jobSize 和 times 控制从request等待队列处理的速度最大每s处理jobSize
     */
    private void acceptLoop() {
        while (true) {
            try {
                ArrayList<BizTask> clients = new ArrayList<>();
                clients.add(queue.take());
                queue.drainTo(clients, jobSize);
                clients.forEach(task -> {
                    CompletableFuture.runAsync(() -> {
                        logger.info(() -> "[1] starting to get the vote data from the request and persistence it.");
                        task.processing();
                    }).orTimeout(task.howMuchTimeLeft().toMillis(), TimeUnit.MILLISECONDS)
                            .whenComplete((v, error) -> {
                                if (error == null) {
                                    task.ok();
                                } else if (error instanceof TimeoutException) {
                                    try {
                                        logger.warn(() -> "request processing is slow, adding to the slowQueue.");
                                        task.timeouted();
                                    } catch (Exception e) {
                                        logger.error(() -> "error when moving to the slowQueue...");
                                        e.printStackTrace();
                                        this.setHealthy(false);
                                        task.unavailable();
                                    }
                                } else {
                                    logger.error(() -> "error when accept queue");
                                    error.printStackTrace();
                                    task.error();
                                }
                            });

                });
                Thread.sleep(1000 / (times > 0 ? times : 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void addToWaitingList(AsyncContext c) throws IOException {
        try {
            BizTask task = new BizTask(c);
            queue.add(task);
            setHealthy(true);
        } catch (IllegalStateException ie) {
            logger.error(() -> "Exceed Server Capacity");
            this.setHealthy(false);
            // 如果加入队列失败，则返回capcity 异常
            ((HttpServletResponse) c.getResponse()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            c.getResponse().getWriter().write("Exceed Server Capacity.");
            c.complete();
        } catch (Exception e) {
            logger.error(() -> "Server Error");
            ((HttpServletResponse) c.getResponse()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            c.getResponse().getWriter().write("Internal_server_error");
            c.complete();
        }
    }
}
