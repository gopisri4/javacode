package cn.kaisay.azure.webapp.voteapp.model;

public class Vote {

    private String email;

    private String sel;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSel() {
        return sel;
    }

    public void setSel(String sel) {
        this.sel = sel;
    }

    public Vote(String email, String sel) {
        this.email = email;
        this.sel = sel;
    }

    @Override
    public String toString() {
        return "Vote{" +
                "email='" + email + '\'' +
                ", sel='" + sel + '\'' +
                '}';
    }
}
