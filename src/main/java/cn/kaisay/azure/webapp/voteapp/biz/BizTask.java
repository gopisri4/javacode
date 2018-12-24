package cn.kaisay.azure.webapp.voteapp.biz;

import cn.kaisay.azure.webapp.voteapp.model.Vote;
import cn.kaisay.azure.webapp.voteapp.web.VoteServlet;
import com.google.gson.Gson;
import com.google.gson.stream.MalformedJsonException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;


public class BizTask {

    private static Logger logger = LogManager.getLogger();
    private final LocalDateTime start = LocalDateTime.now();
    private final StampedLock stampedLock = new StampedLock();
    private volatile Status status = Status.NEW;
    private AsyncContext ac;
    private Vote vote;
    private LocalDateTime timeout = start.plusSeconds(10);

    private Duration processingTime;

    private volatile boolean done;
    private Thread process;

    public BizTask(AsyncContext ac) {
        this.ac = ac;
        HttpServletRequest acc = ((HttpServletRequest) ac.getRequest());
        String email = acc.getParameter("email");
        String sel = acc.getParameter("sel");
        //TODO validate for the Vote Data
        vote = new Vote(email, sel);

    }

    public AsyncContext getAc() {
        return ac;
    }

    public Vote getVote() {
        return vote;
    }

    public HttpServletResponse getResp() {
        return (HttpServletResponse) (getAc().getResponse());
    }

    public HttpServletRequest getReq() {
        return (HttpServletRequest) (getAc().getRequest());
    }

    public void processing() {
        //TODO connect to MySQL and persistence
//        long stamp = stampedLock.tryOptimisticRead();
        status = Status.PROCESSING;
        process = Thread.currentThread();
        try (BufferedReader br = getReq().getReader()) {

            processingTime = Duration.ofMillis(
                    ThreadLocalRandom.current().nextLong(2950, 3100));
            String json = br.lines().collect(Collectors.joining());
            Gson gson = new Gson();
            Vote v = gson.fromJson(json, Vote.class);
            logger.debug(() -> v);
            logger.debug("wait for " + processingTime);
//            stamp = begin();
            Thread.sleep(processingTime.toMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();

//            throw new RuntimeException();
        } catch (MalformedJsonException e) {
            e.printStackTrace();

//            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.error(() -> "error when encoding the json file.", e);
        } catch (Exception e) {
            e.printStackTrace();
//            throw new RuntimeException(e);
        } finally {
            logger.debug("begin done");
            done();
            logger.debug("end done");
        }

        logger.debug("finish task with waiting for " + processingTime);

    }

    private void done() {
        logger.debug(() -> "done()@" + Thread.currentThread().getName());
        long stamp = 0;
        try {
                stamp = stampedLock.writeLock();
                if(status == Status.TIMEOUT) {
                    logger.debug("----timeout ok");
                    slowOk();
                    VoteServlet.slowNumber.decrementAndGet();
                    logger.debug("timeout ok");
                } else if (status == Status.TIMEOUTED) {
                    logger.debug("----timeouted 1");
                    timeouted();
                    VoteServlet.slowNumber.decrementAndGet();
                    logger.debug("----timeouted");
                } else {
                    logger.debug("====ok");
                    ok();
                    logger.debug("----ok");
                }
        } finally {
            stampedLock.unlock(stamp);

        }

    }

    /**
     * wait if the task is done
     */
    public void going() {
        logger.debug(() -> Thread.currentThread().getName() + " try to get the write lock.");
        stampedLock.writeLock();
        logger.debug("going1 " + done);
//        while(!(status == Status.DONE)){
////            if(done) break;
////
//        }
        logger.debug("going2 " + done);
    }


    /**
     * 这种用法是错误的
     */
//    private long begin() {
//        logger.debug(()->"begin()@"+Thread.currentThread().getName());
//        done = false;
//        return  stampedLock.writeLock();
//
//    }

    public void ok() {
        getResp().addHeader("time0", Duration.between(start, LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_OK);
        getAc().complete();
    }

    public void slowOk() {
        logger.warn(() -> "processing time windows is " + processingTime
                + " | with timeLeft is " + timeLeft());
        getResp().addHeader("slowQueuq", "y");
        getResp().addHeader("time0", Duration.between(start, LocalDateTime.now()).toString());
        getResp().addHeader("timeout", "start @ " + start + " timeout @ " + LocalDateTime.now());
        getResp().setStatus(HttpServletResponse.SC_OK);
        getAc().complete();
    }

    public void unavailable() {
        logger.error(() -> "server is unavailable now");
        getResp().addHeader("time0", Duration.between(start, LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        getAc().complete();
    }

    public void error() {
//        logger.error(()->"error when processing",t);
        getResp().addHeader("time0", Duration.between(start, LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        getAc().complete();
    }

    public void timeouted() {
        LocalDateTime t = LocalDateTime.now();
        logger.error(() -> "timeout after total " + Duration.between(start, t)
                + " | processing time windows is " + processingTime
                + " | with timeLeft is " + timeLeft());
        getResp().setStatus(599);
        getResp().addHeader("time0", Duration.between(start, t).toString());
        getResp().addHeader("timeout", "start @ " + start + " timeout @ " + t);
        getAc().complete();
    }

    public Duration timeLeft() {
        Duration past = Duration.between(LocalDateTime.now(), timeout);
        return past.isNegative() ? Duration.ZERO : past;
    }

    public void slow() {
        long stamp = stampedLock.tryOptimisticRead();
        try {
            long ws = stampedLock.tryConvertToWriteLock(stamp);
            //which means no other thread is updating the data
            if (ws != 0) {
                stamp = ws;
                status = Status.TIMEOUT;
            }
        } finally {
            stampedLock.unlock(stamp);
        }

        VoteServlet.slowMonitorScheduler.schedule(() -> {
            long st = stampedLock.writeLock();
            try {
                logger.debug("mark the process is timeout, try to interrupt it.");
                status = Status.TIMEOUTED;
                process.interrupt();
            } finally {
                stampedLock.unlock(st);
            }


        }, timeLeft().toMillis(), TimeUnit.MILLISECONDS);
        VoteServlet.slowNumber.incrementAndGet();
    }

    enum Status {
        NEW,
        STARTING,
        PROCESSING,
        TIMEOUT,
        TIMEOUTED,
        DONE
    }
}

