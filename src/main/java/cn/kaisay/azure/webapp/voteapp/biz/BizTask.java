package cn.kaisay.azure.webapp.voteapp.biz;

import cn.kaisay.azure.webapp.voteapp.model.Vote;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

public class BizTask {

    private static Logger logger = LogManager.getLogger();

    private AsyncContext ac ;

    private Vote vote ;

    private final LocalDateTime start = LocalDateTime.now();

    private LocalDateTime timeout = start.plusSeconds(10);

    private Duration processingTime;

    private volatile boolean done;

    private final StampedLock stampedLock = new StampedLock();

    public AsyncContext getAc() {
        return ac;
    }

    public Vote getVote() {
        return vote;
    }

    public HttpServletResponse getResp() { return (HttpServletResponse)(getAc().getResponse());}

    public HttpServletRequest getReq() { return (HttpServletRequest)(getAc().getRequest());}

    public BizTask(AsyncContext ac) {
        this.ac = ac;
        HttpServletRequest acc  = ((HttpServletRequest)ac.getRequest()) ;
        String email = acc.getParameter("email");
        String sel = acc.getParameter("sel");
        //TODO validate for the Vote Data
        vote = new Vote(email,sel);

    }


    public void processing(){
        //TODO connect to MySQL and persistence
        Random random = new Random();
        processingTime = Duration.ofSeconds(random.nextInt(7));
        try(BufferedReader br = getReq().getReader()){
            String json = br.lines().collect(Collectors.joining());
//            logger.debug(()->json);
            Gson gson = new Gson();
            Vote v = gson.fromJson(json,Vote.class);
            logger.debug(()->v);
        }catch (IOException e) {
            logger.error(()->"error when encoding the json file.",e);
        }
        try {
            logger.debug("wait for "+processingTime);
            Thread.sleep(processingTime.toMillis());
//            Thread.sleep(1000);// test for timeout
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        done();
        logger.debug("finish task with waiting for "+processingTime);

    }

    private void done() {
        long stamp = stampedLock.writeLock();
        try {
            done = true;
        } finally {
            stampedLock.unlock(stamp);
        }

    }

    /**
     * wait if the task is done
     */
    public void going() {
        logger.debug("going1 "+done);

        while(!done){

            if(done) break;
        }
        logger.debug("going2 "+done);
    }

    public void ok() {
        getResp().addHeader("time0",Duration.between(start,LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_OK);
        getAc().complete();
    }

    public void slowOk() {
        logger.warn(()-> "processing time windows is "+processingTime
                +" | with timeLeft is "+timeLeft());
        getResp().addHeader("slowQueuq","y");
        getResp().addHeader("time0",Duration.between(start,LocalDateTime.now()).toString());
        getResp().addHeader("timeout","start @ "+start+" timeout @ "+LocalDateTime.now());
        getResp().setStatus(HttpServletResponse.SC_OK);
        getAc().complete();
    }

    public void unavailable() {
        logger.error(()->"server is unavailable now");
        getResp().addHeader("time0",Duration.between(start,LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        getAc().complete();
    }

    public void error() {
//        logger.error(()->"error when processing",t);
        getResp().addHeader("time0",Duration.between(start,LocalDateTime.now()).toString());
        getResp().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        getAc().complete();
    }

    public void timeout() {
        LocalDateTime t = LocalDateTime.now();
        logger.error(()->"timeout after total " +Duration.between(start,t)
                +" | processing time windows is "+processingTime
                +" | with timeLeft is "+timeLeft());
        getResp().setStatus(599);
        getResp().addHeader("time0",Duration.between(start,t).toString());
        getResp().addHeader("timeout","start @ "+start+" timeout @ "+t);
        getAc().complete();
    }

    public Duration timeLeft() {
        Duration past = Duration.between(LocalDateTime.now(),timeout);
        return past.isNegative()? Duration.ZERO:past;
    }
}

