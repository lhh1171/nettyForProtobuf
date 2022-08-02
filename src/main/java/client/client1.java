package client;

public class client1 {
    public static void main(String[] args) {
        TransClient transClient = new TransClient();
        transClient.bind(8090);
    }
}
