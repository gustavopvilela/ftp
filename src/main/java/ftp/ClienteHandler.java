package ftp;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
                saida.println("PASTA:\t" + pasta.getName());
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
                int tamanhoArquivo = Integer.parseInt(entrada.readLine());

                File arquivoAlvo = new File(pastaAlvo, nomeArquivo);
                arquivoAlvo.getParentFile().mkdirs();

                /* Recebe o conteúdo do arquivo */
                try (FileOutputStream fos = new  FileOutputStream(arquivoAlvo)) {
                    byte[] buffer = new byte[1024];
                    int totalRecebido = 0;

                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = Math.min(buffer.length, tamanhoArquivo - totalRecebido);
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

    private void handleDownloadPasta(String nomePasta) {
        try {
            File pastaFonte = new File(Servidor.getRoot(), nomePasta);
            if (!pastaFonte.exists() || !pastaFonte.isDirectory()) {
                saida.println("550 Pasta não encontrada: " + nomePasta);
                return;
            }

            // 1. INFORMA AO CLIENTE QUE O STREAMING VAI COMEÇAR
            saida.println("150 Iniciando streaming da pasta como arquivo ZIP.");
            saida.flush(); // Garante que o cliente receba esta mensagem antes do stream de bytes

            // 2. CRIA UM ZIP STREAM DIRETAMENTE PARA O OUTPUTSTREAM DO SOCKET
            // O try-with-resources vai fechar o zos e o socket subjacente ao final,
            // o que sinaliza o fim da transmissão para o cliente.
            try (ZipOutputStream zos = new ZipOutputStream(cliente.getOutputStream())) {
                adicionarPastaAoZip(pastaFonte, pastaFonte, zos);
            } // <- AQUI O ZOS É FECHADO, O SOCKET É FECHADO, E A OPERAÇÃO TERMINA.

            System.out.println("Transferência por stream para o cliente " + cliente.getInetAddress() + " concluída.");

        } catch (IOException e) {
            // A exceção "Socket closed" ou "Connection reset by peer" pode acontecer aqui
            // se o cliente desconectar abruptamente, o que é um comportamento esperado.
            System.err.println("Erro durante o streaming para o cliente " + cliente.getInetAddress() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro inesperado no download (ZIP Stream): " + e.getMessage());
        }
        // Não há mais código aqui. A thread do handler terminará naturalmente.
    }

    // O método adicionarPastaAoZip permanece o mesmo.
    private void adicionarPastaAoZip(File pasta, File pastaBase, ZipOutputStream zos) throws IOException {
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

    /* Envia os arquivos para o cliente recursivamente */
    private void enviarArquivos(File pasta, File pastaBase) throws IOException {
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) {
            return;
        }

        // Ordena para manter a consistência
        Arrays.sort(arquivos);

        for (File arquivo : arquivos) {
            if (arquivo.isDirectory()) {
                // Se for um diretório, chama a função recursivamente
                enviarArquivos(arquivo, pastaBase);
            } else {
                // Se for um arquivo, envia seus dados

                // 1. Calcula o caminho relativo para manter a estrutura de pastas no cliente
                String caminhoRelativo = pastaBase.toPath().relativize(arquivo.toPath()).toString();
                caminhoRelativo = caminhoRelativo.replace(File.separator, "/");

                // 2. Envia os metadados do arquivo (nome e tamanho)
                saida.println("FILE:" + caminhoRelativo);
                saida.println(arquivo.length());
                saida.flush(); // ESSENCIAL: Garante que o cliente receba os metadados antes dos dados

                // 3. Envia o conteúdo binário do arquivo
                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    byte[] buffer = new byte[4096];
                    int bytesLidos;
                    while ((bytesLidos = fis.read(buffer)) != -1) {
                        cliente.getOutputStream().write(buffer, 0, bytesLidos);
                    }
                    cliente.getOutputStream().flush(); // ESSENCIAL: Garante que todos os bytes do arquivo foram enviados
                }

                // 4. Aguarda a confirmação do cliente para sincronizar a transferência
                String confirmacao = entrada.readLine(); // Espera o "OK" do cliente
                if (confirmacao == null || !confirmacao.equals("OK")) {
                    System.err.println("Falha na confirmação do cliente para o arquivo: " + caminhoRelativo);
                    throw new IOException("Confirmação do cliente falhou.");
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