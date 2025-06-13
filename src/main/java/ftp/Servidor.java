package ftp;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {
    private static final int PORTA = 12381;
    private static final String ROOT = "root";
    private ServerSocket servidor;
    private final ExecutorService pool;
    private boolean running = false;

    public Servidor() {
        this.pool = Executors.newFixedThreadPool(10);
        try {
            Files.createDirectories(Paths.get(ROOT));
        }
        catch (IOException e) {
            System.err.println("Erro ao criar diretório no servidor: " + e.getMessage());
        }
    }

    /* Inicia o servidor e aguarda conexões */
    public void start () {
        try {
            servidor = new ServerSocket(PORTA);
            running = true;
            System.out.println("Servidor iniciado na porta " + PORTA);
            System.out.println("Pasta raiz: " + new File(ROOT).getAbsolutePath());

            while (running) {
                try {
                    Socket cliente = servidor.accept();
                    System.out.println("Nova conexão aceita: " + cliente.getInetAddress());
                    pool.submit(new ClienteHandler(cliente));
                }
                catch (IOException e) {
                    if (running) {
                        System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                    }
                }
            }
        }
        catch (IOException e) {
            System.err.println("Erro ao criar servidor: " + e.getMessage());
        }
    }

    public void stop () {
        running = false;
        try {
            if (servidor != null) {
                servidor.close();
            }
            pool.shutdown();
        }
        catch (IOException e) {
            System.err.println("Erro ao parar servidor: " + e.getMessage());
        }
    }

    public static String getRoot() {
        return ROOT;
    }
}