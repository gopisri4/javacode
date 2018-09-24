package cn.kaisay.azure.webapp.voteapp;

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

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        addToWaitingList(request.startAsync());
    }

    private static ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private static ExecutorService es = Executors.newCachedThreadPool();
    private static final BlockingQueue<AsyncContext> queue = new ArrayBlockingQueue<>(20000);

    /**
     * 最大每次100个线程同时处理
     */
    private static final int jobSize = 100;

    /**
     * 每s最大批处理量
     */
    private static final int times = 20;

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
                    ((HttpServletResponse)ac.getResponse()).setStatus(200);
                    ac.complete();
                });
                Thread.sleep(1000/(times>0?times:1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }


    public static void addToWaitingList(AsyncContext c) {
        queue.add(c);
    }
}
