package cn.kaisay.azure.webapp.voteapp;

//import jdk.incubator.http.HttpHeaders;

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

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        addToWaitingList(request.startAsync());
    }


    /**
     *
     * @param request
     * @param response
     * @throws ServletException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws  ServletException {
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

        System.out.println("Set the jobsize to "+jobSize+" and times to "+times);
    }

    private static ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private static ExecutorService es = Executors.newCachedThreadPool();
    private static final BlockingQueue<AsyncContext> queue = new ArrayBlockingQueue<>(20000);

    /**
     * 最大每次100个线程同时处理
     */
    private static int jobSize = 100;

    /**
     * 每s最大批处理量
     */
    private static int times = 20;

    static {
//        executorService.scheduleAtFixedRate(VoteServlet::newEvent, 0, 2, TimeUnit.SECONDS);
        Executors.newSingleThreadExecutor().submit(()->{loop();});

    }

    private static void newEvent() {
        ArrayList<AsyncContext> clients = new ArrayList<>(queue.size());
        queue.drainTo(clients);
        clients.parallelStream().forEach( ac -> {
//            ServletUtil.writeResponse(ac.getResponse(), "OK");
            // connect to db and insert into db
            ((HttpServletResponse)ac.getResponse()).setStatus(200);
            ac.complete();
        });
    }

    /**
     * 每s的总处理能力上限为jobSize * times ，因为服务器的硬件处理能力有限，需要限制此数值大小
     */
    private static void loop() {
        while(true) {
/*            es.submit(()->{
                try {
                    AsyncContext ac = queue.take();
                    ((HttpServletResponse)ac.getResponse()).setStatus(200);
                    ac.complete();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });*/
            try {
                ArrayList<AsyncContext> clients = new ArrayList<>();
                clients.add(queue.take());
                queue.drainTo(clients, jobSize);
                clients.parallelStream().forEach( ac -> {
//            ServletUtil.writeResponse(ac.getResponse(), "OK");
                    // connect to db and insert into db
                    ((HttpServletResponse)ac.getResponse()).setStatus(HttpServletResponse.SC_OK);
                    ac.complete();
                });
                Thread.sleep(1000/(times>0?times:1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }


    public static void addToWaitingList(AsyncContext c) throws IOException {
        try {
            queue.add(c);
        } catch (Exception e) {
            logger.error("Exceed Server Capacity");
            // 如果加入队列失败，则返回capcity 异常
            ((HttpServletResponse)c.getResponse()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            /* if JDK9
            final HttpHeaders headers = new HttpHeaders();
                final Integer retryAfterMillis = command.getProperties()

            .circuitBreakerSleepWindowInMilliseconds().get();

            headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterMillis / 1000));
            */
            c.getResponse().getWriter().write("Exceed Server Capacity.");
            c.complete();
        }
    }
}
