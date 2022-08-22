import javax.xml.crypto.Data;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.NoSuchFileException;
import java.util.NoSuchElementException;

public class DuckHuntServer {

    DuckHuntServer() {
        ServerSocket serverSocket;
        serverSocket = startServer(12345);

        while (true) {
            DuckHunt duckHunt = new DuckHunt();
            int numMaxPlayers = duckHunt.numMaxPlayers();
            for (int i = 0; i < numMaxPlayers; i++) {
                Socket clientSocket = waitClient(serverSocket);
                duckHunt.addPlayer(clientSocket);
            }
            duckHunt.startLogic(new Logic(duckHunt));
            duckHunt.start();
        }
    }

    ServerSocket startServer(int port) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("Cant hear port: " + port + "\n" + e);
            System.exit(1);
        }
        return serverSocket;
    }

    Socket waitClient(ServerSocket serverSocket) {
        Socket clientSocket = null;
        try {
            System.out.println("Waiting player to connect");
            clientSocket = serverSocket.accept();
        } catch (IOException e) {
            System.out.println("Accept failed: " + serverSocket.getLocalPort() + "\n" + e);
            System.exit(1);
        }
        System.out.println("Accept confirmed");
        return clientSocket;
    }

    public static void main(String[] args) {
        new DuckHuntServer();
    }
}

class DuckHunt {
    Socket clientSocket;
    DataOutputStream[] os = new DataOutputStream[2];
    boolean hasData = true;
    Logic logic;
    boolean clientAlive[] = {true, true};
    int countConnectedPlayers = 0;

    public int numMaxPlayers() {
        return 2;
    }

    public void addPlayer(Socket clientSocket) {
        this.clientSocket = clientSocket;
        int clientPlayerNumber = countConnectedPlayers++;
        try {
            os[clientPlayerNumber] = new DataOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            hasData = false;
            e.printStackTrace();
        }
        startClientThread(clientSocket, clientPlayerNumber);
    }

    void startClientThread(Socket clientSocket, int clientPlayerNumber) {
        int playerNumber = clientPlayerNumber;
        int opponentNumber = 1 - playerNumber;

        new Thread() {
            public void run() {
                try {
                    DataInputStream is = new DataInputStream(clientSocket.getInputStream());
                    String eventType;

                    do {
                        eventType = is.readUTF();
                        switch (eventType) {
                            case "MM":
                                logic.moveGun(playerNumber, is.readInt(), is.readInt());
                                synchronized (logic) {
                                    sendEventType("MOVE", playerNumber);
                                    sendEventType("MOVE", opponentNumber);
                                }
                                break;
                            case "MP":
                                if (logic.hitShot(playerNumber, is.readInt(), is.readInt())) {
                                    synchronized (logic) {

                                        if (logic.points[1] >= 5) {
                                            sendEventType("WINNER", playerNumber);
                                            sendEventType("LOSER", opponentNumber);
                                            sendScore(playerNumber);
                                            sendScore(opponentNumber);
                                            logic.points[0] = 0;
                                            logic.points[1] = 0;
                                        } else if (logic.points[0] >= 5) {
                                            sendEventType("WINNER", opponentNumber);
                                            sendEventType("LOSER", playerNumber);
                                            sendScore(playerNumber);
                                            sendScore(opponentNumber);
                                            logic.points[0] = 0;
                                            logic.points[1] = 0;
                                        } else {
                                            sendEventType("HIT SHOT", playerNumber);
                                            sendEventType("OPPONENT HIT SHOT", opponentNumber);
                                            sendScore(playerNumber);
                                            sendScore(opponentNumber);
                                        }
                                    }
                                    forceFlush();
                                    sleep(1000);
                                } else {
                                    synchronized (logic) {
                                        sendEventType("MISS", playerNumber);
                                        sendEventType("OPPONENT MISS", opponentNumber);
                                    }
                                }
                                break;
                        }
                        forceFlush();
                    } while (clientAlive[playerNumber] && clientAlive[opponentNumber]);
                    os[playerNumber].close();
                    is.close();;
                    clientSocket.close();
                } catch (IOException e) {
                    try {
                        clientSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } catch (NoSuchElementException e) {

                } catch (InterruptedException e) {
//                    faz nada
                }
            }
        }.start();
    }

    void sendEventType(String eventType, int playerNumber) {
        send(eventType, playerNumber);
        sendPositions(playerNumber);
        forceFlush();
    }

    void send(String s, int playerNumber) {
        int opponentNumber = 1 - playerNumber;
        if (clientAlive[playerNumber]) {
            try {
                os[playerNumber].writeUTF(s);
            } catch (IOException e) {
                clientAlive[playerNumber] = false;
                sendEventType("WINNER", opponentNumber);
                sendScore(opponentNumber);
            }
        }
    }

    void send(int i, int playerNumber) {
        int opponentNumber = 1 - playerNumber;
        if (clientAlive[playerNumber]) {
            try {
                os[playerNumber].writeInt(i);
            } catch (IOException e) {
                clientAlive[playerNumber] = false;
                sendEventType("WINNER", opponentNumber);
                sendScore(opponentNumber);
            }
        }
    }

    void sendPositions(int playerNumber) {
        int opponentNumber = 1 - playerNumber;
        sendPosition(logic.positionDuck(), playerNumber);
        sendPosition(logic.positionGun(playerNumber), playerNumber);
        sendPosition(logic.positionGun(opponentNumber), playerNumber);
    }

    void sendPosition(Position p, int playerNumber) {
        send(p.x, playerNumber);
        send(p.y, playerNumber);
    }

    public void startLogic(Logic logic) {
        this.logic = logic;
    }

    public void start() {
        startCountdown();
        logic.running = true;
        logic.execute();
    }

    public void startCountdown() {
        int playerNumber = 0;
        int opponentNumber = 1- playerNumber;
        try {
            send("3", playerNumber);
            send("3", opponentNumber);
            Thread.sleep(1000);
            send("2", playerNumber);
            send("2", opponentNumber);
            Thread.sleep(1000);
            send("1", playerNumber);
            send("1", opponentNumber);
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
    }

    public void sendScore(int playerNumber) {
        int opponentNumber = 1 - playerNumber;
        send(logic.points[playerNumber], playerNumber);
        send(logic.points[opponentNumber], playerNumber);
    }

    public void forceFlush() {
        try {
            os[0].flush();
            os[1].flush();
        } catch (IOException e) {

        }
    }
}

class Logic {
    DuckHunt duckHunt;
    public Position positionGun[] = { new Position(0,0), new Position(0,0)};
    public boolean running = false, duckAlive = false;
    public Rectangle duckSensitiveArea = new Rectangle(0, 0, 50, 50);
    final Dimension gameArea = new Dimension(800, 600);
    public int[] points = new int[2];

    Logic(DuckHunt duckHunt) {
        this.duckHunt = duckHunt;
    }

    public void moveGun(int playerNumber, int x, int y) {
        positionGun[playerNumber].x = x;
        positionGun[playerNumber].y = y;
    }

    public Position positionGun(int playerNumber) {
        return positionGun[playerNumber];
    }

    public Position positionDuck() {
        return new Position(duckSensitiveArea.x, duckSensitiveArea.y);
    }

    public void execute() {
        startTimer();
    }

    public void startTimer() {
        new Thread() {
            @Override
            public void run() {
                while (duckHunt.clientAlive[0] && duckHunt.clientAlive[1]) {
                    try {
                        logicTimer();
                        sleep(1000);
                    } catch (InterruptedException e) {

                    }
                }
            }
        }.start();
    }

    public void logicTimer() {
        newDuckPosition();
        synchronized (this) {
            duckHunt.sendEventType("MOVE", 0);
            duckHunt.sendEventType("MOVE", 1);
            duckHunt.forceFlush();
        }
    }

    public void newDuckPosition() {
        duckAlive = true;
        duckSensitiveArea.x = (int) (Math.random() * (gameArea.width - duckSensitiveArea.width));
        duckSensitiveArea.y = (int) (Math.random() * (gameArea.height - duckSensitiveArea.height));
    }

    public boolean hitShot(int playerNumber, int x, int y) {
        if (duckSensitiveArea.contains(x, y)) {
            duckAlive = false;
            points[playerNumber]++;
            return true;
        }
        return false;
    }

}