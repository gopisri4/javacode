package cn.kaisay.azure.webapp.voteapp.web;

import java.util.Optional;

public class Setup {

    private Optional<Integer> jobSize = Optional.of(10);

    private Optional<Integer> times = Optional.of(100);

    public Optional<Integer> getJobSize() {
        return jobSize;
    }

    public void setJobSize(Optional<Integer> jobSize) {
        this.jobSize = jobSize;
    }

    public Optional<Integer> getTimes() {
        return times;
    }

    public void setTimes(Optional<Integer> times) {
        this.times = times;
    }
}
