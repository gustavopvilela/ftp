package ftp;

import java.io.*;
import java.net.Socket;

/* Classe de handler de cliente para o servidor. */
public class ClienteHandler implements Runnable {
    private final Socket cliente;
    private BufferedReader entrada;
    private PrintWriter saida;
    private String pastaAtual = Servidor.getRoot();

    public ClienteHandler(Socket cliente) {
        this.cliente = cliente;
    }

    @Override
    public void run() {
        try {
            /* Configuração dos sistemas de comunicação */
            entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            saida = new PrintWriter(cliente.getOutputStream(), true);

            saida.println("220 Servidor Pronto");

            String comando;
            while ((comando = entrada.readLine()) != null) {
                System.out.println("Comando recebido: " + comando);
                processarComando(comando);
            }
        }
        catch (IOException e) {
            System.err.println("Erro na comunicação com cliente: " + e.getMessage());
        }
        finally {
            fecharConexao();
        }
    }

    private void processarComando (String comando) {
        String[] partes =  comando.split(" ", 2);
        String cmd = partes[0].toUpperCase();
        String args = partes.length > 1 ? partes[1] : "";

        switch (cmd) {
            case "LIST":
                handleComandoLista();
                break;
            case "UPLOAD_FOLDER":
                handleUploadPasta(args);
                break;
            case "DOWNLOAD_FOLDER":
                handleDownloadPasta(args);
                break;
            case "CHECK_FOLDER":
                handleChecarPasta(args);
                break;
            case "QUIT":
                saida.println("221 Conexão encerrada");
                fecharConexao();
                break;
            default:
                saida.println("500 Comando não reconhecido");
        }
    }

    private void handleComandoLista () {
        try {
            File pastaServidor = new File(Servidor.getRoot());
            File[] pastas = pastaServidor.listFiles(File::isDirectory);

            if (pastas == null || pastas.length == 0) {
                saida.println("150 Nenhuma pasta encontrada");
                saida.println("226 Lista completa");
                return;
            }

            saida.println("150 Listando pastas");
            for (File pasta : pastas) {
                saida.println("PASTA:" + pasta.getName());
            }
            saida.println("226 Lista completa");
        }
        catch (Exception e) {
            saida.println("550 Erro ao listar pastas: " + e.getMessage());
        }
    }

    private void handleChecarPasta (String pastaInfo) {
        try {
            String[] info = pastaInfo.split("\\|");
            if (info.length != 2) {
                saida.println("500 Formato inválido. Use: nome|id");
                return;
            }

            String nomePasta = info[0];
            String idPasta = info[1];
            String nomeAlvo = nomePasta + "_" + idPasta;

            File pastaAlvo = new File(Servidor.getRoot(), nomeAlvo);
            if (pastaAlvo.exists()) {
                saida.println("250 Pasta existe: " + nomeAlvo);
            }
            else {
                saida.println("450 Pasta não existe");
            }
        }
        catch (Exception e) {
            saida.println("550 Erro ao verificar pasta: " + e.getMessage());
        }
    }

    private void handleUploadPasta (String pastaInfo) {
        try {
            String[] info = pastaInfo.split("\\|");
            if (info.length != 2) {
                saida.println("500 Formato inválido. Use: nome|id");
                return;
            }

            String nomePasta = info[0];
            String idPasta = info[1];
            String nomeAlvo = nomePasta + "_" + idPasta;

            File pastaAlvo = new File(Servidor.getRoot(), nomeAlvo);

            /* Remove a pasta existente se houver */
            if (pastaAlvo.exists()) {
                deletarPasta(pastaAlvo);
            }

            pastaAlvo.mkdirs();
            saida.println("150 Pronto para receber pasta: " + nomeAlvo);
            receberArquivos(pastaAlvo);
            saida.println("226 Upload da pasta concluído com sucesso");
        }
        catch (Exception e) {
            saida.println("550 Erro no upload: " + e.getMessage());
        }
    }

    private void receberArquivos (File pastaAlvo) throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.equals("END_FOLDER")) {
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivo = linha.substring(5);
                long tamanhoArquivo = Long.parseLong(entrada.readLine());

                File arquivoAlvo = new File(pastaAlvo, nomeArquivo);

                File diretorioPai = arquivoAlvo.getParentFile();
                if (diretorioPai != null && !diretorioPai.exists()) {
                    boolean criado = diretorioPai.mkdirs();
                    if (!criado) {
                        System.err.println("ERRO: Não foi possível criar diretório: " + diretorioPai.getAbsolutePath());
                        saida.println("ERROR");
                        continue;
                    }
                    System.out.println("Diretório criado: " + diretorioPai.getAbsolutePath());
                }

                /* Recebe o conteúdo do arquivo */
                try (FileOutputStream fos = new  FileOutputStream(arquivoAlvo)) {
                    byte[] buffer = new byte[1024];
                    long totalRecebido = 0;

                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = (int) Math.min(buffer.length, tamanhoArquivo - totalRecebido);
                        int bytesLidos = cliente.getInputStream().read(buffer, 0, bytesParaLer);

                        if (bytesLidos == -1) break;
                        fos.write(buffer, 0, bytesLidos);
                        totalRecebido += bytesLidos;
                    }
                }

                saida.println("OK");
            }
        }
    }

    /* Gerencia o download da pasta */
    private void handleDownloadPasta (String nomePasta) {
        try {
            File pastaFonte = new File(Servidor.getRoot(), nomePasta);
            if (!pastaFonte.exists() || !pastaFonte.isDirectory()) {
                saida.println("550 Pasta não encontrada: " + nomePasta);
                return;
            }

            saida.println("150 Iniciando nome da pasta: " + nomePasta);

            enviarArquivos(pastaFonte, pastaFonte);

            saida.println("END_FOLDER");
            saida.println("226 Download concluído");
        }
        catch (Exception e) {
            saida.println("550 Erro no download: " + e.getMessage());
        }
    }

    /* Envia os arquivos para o cliente recursivamente */
    private void enviarArquivos (File pasta, File pastaBase) throws IOException {
        File[] arquivos = pasta.listFiles();
        if (arquivos == null || arquivos.length == 0) return;

        java.util.Arrays.sort(arquivos, java.util.Comparator.comparing(File::getName));

        for (File arquivo : arquivos) {
            if (arquivo.isDirectory()) {
                enviarArquivos(arquivo, pastaBase);
            }
            else {
                /* Verifica o caminho relativo */
                String caminhoRelativo = pastaBase.toPath().relativize(arquivo.toPath()).toString();

                saida.println("FILE:" + caminhoRelativo.replace("\\","/"));
                saida.println(arquivo.length());

                /* Envia o conteúdo do arquivo */
                long bytesEnviados = 0;
                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    byte[] buffer = new byte[1024];
                    int bytesLidos;

                    while ((bytesLidos = fis.read(buffer)) != -1) {
                        cliente.getOutputStream().write(buffer, 0, bytesLidos);
                        bytesEnviados += bytesLidos;
                    }
                    cliente.getOutputStream().flush();
                }

                String resposta = entrada.readLine();
                if ("OK".equals(resposta)) {
                    System.out.println("✓ Confirmado: " + caminhoRelativo + " (" + bytesEnviados + " bytes)");
                } else {
                    System.err.println("✗ Erro na confirmação: " + caminhoRelativo + " - Resposta: " + resposta);
                }
            }
        }
    }

    private void deletarPasta (File pasta) {
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    deletarPasta(arquivo);
                }
                else {
                    arquivo.delete();
                }
            }
        }
        pasta.delete();
    }

    private void fecharConexao () {
        try {
            if (entrada != null) entrada.close();
            if (saida != null) saida.close();
            if (cliente != null) cliente.close();
        }
        catch (Exception e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Servidor servidor = new Servidor();

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::stop));

        servidor.start();
    }
}