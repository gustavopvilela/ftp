package ftp.servidor;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import utils.FolderIdUtil;

/* Classe de handler de cliente para o servidor. */
public class ClienteHandler implements Runnable {
    private final Socket cliente;
    private BufferedReader entrada;
    private PrintWriter saida;
    private String pastaAtual = Servidor.getRoot();
    private final Consumer<String> logger; // Logger da GUI
    private final String clienteId;

    public ClienteHandler(Socket cliente, Consumer<String> logger) {
        this.cliente = cliente;
        this.logger = logger;
        // Identificador único para este cliente no log
        this.clienteId = cliente.getInetAddress().getHostAddress() + ":" + cliente.getPort();
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
                log("Comando recebido: " + comando);
                processarComando(comando);
            }
        } catch (IOException e) {
            log("ERRO na comunicação: " + e.getMessage());
        } finally {
            log("Conexão encerrada.");
            fecharConexao();
        }
    }

    // Método auxiliar para adicionar o ID do cliente ao log
    private void log(String mensagem) {
        logger.accept(String.format("[%s] %s", clienteId, mensagem));
    }

    private void processarComando(String comando) {
        String[] partes = comando.split(" ", 2);
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
                log("Comando não reconhecido: " + cmd);
        }
    }

    private void handleComandoLista() {
        try {
            File pastaServidor = new File(Servidor.getRoot());
            File[] pastas = pastaServidor.listFiles(File::isDirectory);

            if (pastas == null || pastas.length == 0) {
                saida.println("150 Nenhuma pasta encontrada");
                saida.println("226 Lista completa");
                log("Nenhuma pasta encontrada para listar.");
                return;
            }

            saida.println("150 Listando pastas com detalhes");
            log("Listando " + pastas.length + " pasta(s) com detalhes.");

            for (File pasta : pastas) {
                String nomeComId = pasta.getName();
                String id = FolderIdUtil.extrairId(nomeComId);
                String nomeOriginal = FolderIdUtil.extrairNomeOriginal(nomeComId);
                long tamanho = FolderIdUtil.calcularTamanhoPasta(pasta); // Calcula o tamanho

                // Novo formato: PASTA_INFO:id|nomeOriginal|tamanho
                saida.println(String.format("PASTA_INFO:%s|%s|%d", id, nomeOriginal, tamanho));
            }
            saida.println("226 Lista completa");
        } catch (Exception e) {
            saida.println("550 Erro ao listar pastas: " + e.getMessage());
            log("ERRO ao listar pastas: " + e.getMessage());
        }
    }

    private void handleChecarPasta(String pastaInfo) {
        try {
            String[] info = pastaInfo.split("\\|");
            if (info.length != 2) {
                saida.println("500 Formato inválido. Use: nome|id");
                log("Formato inválido para CHECK_FOLDER: " + pastaInfo);
                return;
            }

            String nomePasta = info[0];
            String idPasta = info[1];
            String nomeAlvo = nomePasta + "_" + idPasta;

            File pastaAlvo = new File(Servidor.getRoot(), nomeAlvo);
            if (pastaAlvo.exists()) {
                saida.println("250 Pasta existe: " + nomeAlvo);
                log("Pasta '" + nomeAlvo + "' já existe.");
            } else {
                saida.println("450 Pasta não existe");
                log("Pasta '" + nomeAlvo + "' não existe.");
            }
        } catch (Exception e) {
            saida.println("550 Erro ao verificar pasta: " + e.getMessage());
            log("ERRO ao verificar pasta: " + e.getMessage());
        }
    }

    private void handleUploadPasta(String pastaInfo) {
        try {
            String[] info = pastaInfo.split("\\|");
            if (info.length != 2) {
                saida.println("500 Formato inválido. Use: nome|id");
                log("Formato inválido para UPLOAD_FOLDER: " + pastaInfo);
                return;
            }

            String nomePasta = info[0];
            String idPasta = info[1];
            String nomeAlvo = nomePasta + "_" + idPasta;
            log("Iniciando upload para: " + nomeAlvo);

            File pastaAlvo = new File(Servidor.getRoot(), nomeAlvo);

            if (pastaAlvo.exists()) {
                log("Pasta '" + nomeAlvo + "' já existe. Deletando a versão antiga.");
                deletarPasta(pastaAlvo);
            }

            pastaAlvo.mkdirs();
            saida.println("150 Pronto para receber pasta: " + nomeAlvo);
            receberArquivos(pastaAlvo);
            saida.println("226 Upload da pasta concluído com sucesso");
            log("Upload da pasta '" + nomeAlvo + "' concluído com sucesso.");
        } catch (Exception e) {
            saida.println("550 Erro no upload: " + e.getMessage());
            log("ERRO no upload: " + e.getMessage());
        }
    }

    private void receberArquivos(File pastaAlvo) throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.equals("END_FOLDER")) {
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivo = linha.substring(5);
                int tamanhoArquivo = Integer.parseInt(entrada.readLine());
                log("Recebendo arquivo: " + nomeArquivo + " (" + tamanhoArquivo + " bytes)");

                File arquivoAlvo = new File(pastaAlvo, nomeArquivo);
                arquivoAlvo.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(arquivoAlvo)) {
                    byte[] buffer = new byte[8192];
                    int totalRecebido = 0;
                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = Math.min(buffer.length, tamanhoArquivo - totalRecebido);
                        int bytesLidos = cliente.getInputStream().read(buffer, 0, bytesParaLer);
                        if (bytesLidos == -1) break;
                        fos.write(buffer, 0, bytesLidos);
                        totalRecebido += bytesLidos;
                    }
                }
                saida.println("OK"); // Confirmação de recebimento
            }
        }
    }

    private void handleDownloadPasta(String nomePasta) {
        log("Iniciando download da pasta: " + nomePasta);
        try {
            File pastaFonte = new File(Servidor.getRoot(), nomePasta);
            if (!pastaFonte.exists() || !pastaFonte.isDirectory()) {
                saida.println("550 Pasta não encontrada: " + nomePasta);
                log("ERRO: Tentativa de baixar pasta inexistente: " + nomePasta);
                return;
            }

            saida.println("150 Iniciando streaming da pasta como arquivo ZIP.");
            saida.flush();

            try (ZipOutputStream zos = new ZipOutputStream(cliente.getOutputStream())) {
                adicionarPastaAoZip(pastaFonte, pastaFonte, zos);
            }
            log("Transferência por stream para o cliente concluída.");

        } catch (IOException e) {
            log("ERRO durante o streaming para o cliente: " + e.getMessage());
        } catch (Exception e) {
            log("ERRO inesperado no download (ZIP Stream): " + e.getMessage());
        }
    }

    private void adicionarPastaAoZip(File pasta, File pastaBase, ZipOutputStream zos) throws IOException {
        // ... (o conteúdo deste método permanece o mesmo)
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) return;
        Arrays.sort(arquivos);

        byte[] buffer = new byte[8192];

        for (File arquivo : arquivos) {
            if (arquivo.isDirectory()) {
                adicionarPastaAoZip(arquivo, pastaBase, zos);
                continue;
            }

            String caminhoRelativo = pastaBase.toPath().relativize(arquivo.toPath()).toString().replace(File.separator, "/");
            ZipEntry zipEntry = new ZipEntry(caminhoRelativo);
            zos.putNextEntry(zipEntry);

            try (FileInputStream fis = new FileInputStream(arquivo)) {
                int bytesLidos;
                while ((bytesLidos = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesLidos);
                }
            }
            zos.closeEntry();
        }
    }

    private void deletarPasta(File pasta) {
        // ... (o conteúdo deste método permanece o mesmo)
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    deletarPasta(arquivo);
                } else {
                    arquivo.delete();
                }
            }
        }
        pasta.delete();
    }

    private void fecharConexao() {
        try {
            if (entrada != null) entrada.close();
            if (saida != null) saida.close();
            if (cliente != null && !cliente.isClosed()) cliente.close();
        } catch (Exception e) {
            log("ERRO ao fechar conexão: " + e.getMessage());
        }
    }

    // O main antigo foi removido, pois a aplicação agora é iniciada pela ServidorGUI
}