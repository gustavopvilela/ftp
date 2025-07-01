package ftp;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Servidor {
    private final int porta;
    private static final String ROOT = "root";
    private ServerSocket servidorSocket;
    private final ExecutorService pool;
    private volatile boolean running = false;
    private final Consumer<String> logger; // Interface funcional para logar na GUI

    public Servidor(int porta, Consumer<String> logger) {
        this.porta = porta;
        this.logger = logger;
        this.pool = Executors.newFixedThreadPool(10);
        try {
            Files.createDirectories(Paths.get(ROOT));
        } catch (IOException e) {
            logger.accept("ERRO: Falha ao criar diretório raiz: " + e.getMessage());
        }
    }

    /* Inicia o servidor e aguarda conexões */
    public void start() {
        if (running) {
            logger.accept("AVISO: Servidor já está em execução.");
            return;
        }
        try {
            servidorSocket = new ServerSocket(porta);
            running = true;
            logger.accept("Servidor iniciado na porta " + porta);
            logger.accept("Pasta raiz: " + new File(ROOT).getAbsolutePath());

            while (running) {
                try {
                    Socket clienteSocket = servidorSocket.accept();
                    logger.accept("Nova conexão aceita: " + clienteSocket.getInetAddress().getHostAddress());
                    // Passa o logger para o ClienteHandler
                    pool.submit(new ClienteHandler(clienteSocket, logger));
                } catch (SocketException e) {
                    if (!running) {
                        logger.accept("Servidor foi parado. Fechando socket de escuta.");
                    } else {
                        logger.accept("ERRO: Erro no socket do servidor: " + e.getMessage());
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.accept("ERRO: Falha ao aceitar conexão de cliente: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.accept("ERRO FATAL: Não foi possível iniciar o servidor na porta " + porta + ". Detalhes: " + e.getMessage());
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdown();
            }
            logger.accept("Servidor finalizado.");
        }
    }

    public void stop() {
        if (!running) {
            logger.accept("AVISO: Servidor já está parado.");
            return;
        }
        running = false;
        try {
            if (servidorSocket != null && !servidorSocket.isClosed()) {
                servidorSocket.close(); // Isso irá interromper o accept() e lançar uma SocketException
            }
        } catch (IOException e) {
            logger.accept("ERRO: Problema ao fechar o socket do servidor: " + e.getMessage());
        } finally {
            pool.shutdownNow(); // Força o encerramento das threads dos clientes
            logger.accept("Processo de parada do servidor iniciado.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public static String getRoot() {
        return ROOT;
    }
}