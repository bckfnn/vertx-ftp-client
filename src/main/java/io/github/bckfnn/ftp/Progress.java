package io.github.bckfnn.ftp;

public class Progress {
    private FtpClient client;
    private int count;
    
    
    public Progress(FtpClient client, int count) {
        super();
        this.client = client;
        this.count = count;
    }

    public FtpClient client() {
        return client;
    }

    public int count() {
        return count;
    };
}
