package ftp;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ClienteHandler implements Runnable {
    private final Socket cliente;
    private BufferedReader entrada;
    private PrintWriter saida;

    public ClienteHandler(Socket cliente) {
        this.cliente = cliente;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            saida = new PrintWriter(cliente.getOutputStream(), true); // Auto-flush habilitado

            saida.println("220 Servidor Pronto");

            String comando;
            while ((comando = entrada.readLine()) != null) {
                System.out.println("Comando recebido: " + comando);
                processarComando(comando);
            }
        } catch (IOException e) {
            System.err.println("Erro na comunicação com cliente: " + e.getMessage());
        } finally {
            fecharConexao();
        }
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
            case "QUIT":
                saida.println("221 Conexão encerrada");
                fecharConexao();
                break;
            default:
                saida.println("500 Comando não reconhecido");
        }
    }

    private void handleComandoLista() {
        try {
            File pastaServidor = new File(Servidor.getRoot());
            File[] pastas = pastaServidor.listFiles(File::isDirectory);
            saida.println("150 Listando pastas");
            if (pastas != null) {
                Arrays.sort(pastas, Comparator.comparing(File::getName));
                for (File pasta : pastas) {
                    saida.println("PASTA:" + pasta.getName());
                }
            }
            saida.println("226 Lista completa");
        } catch (Exception e) {
            saida.println("550 Erro ao listar pastas: " + e.getMessage());
        }
    }

    private void handleUploadPasta(String nomePastaComId) {
        try {
            File pastaAlvo = new File(Servidor.getRoot(), nomePastaComId);

            // Apaga a pasta antiga para garantir uma sincronização limpa.
            if (pastaAlvo.exists()) {
                deletarPasta(pastaAlvo);
            }
            pastaAlvo.mkdirs();

            saida.println("150 Pronto para receber pasta: " + nomePastaComId);

            receberArquivos(pastaAlvo);

            saida.println("226 Upload da pasta concluído com sucesso");
        } catch (Exception e) {
            saida.println("550 Erro no upload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void receberArquivos(File pastaAlvo) throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.equals("END_FOLDER")) {
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivoRelativo = linha.substring(5);
                long tamanhoArquivo = Long.parseLong(entrada.readLine());
                File arquivoAlvo = new File(pastaAlvo, nomeArquivoRelativo);

                File diretorioPai = arquivoAlvo.getParentFile();
                if (diretorioPai != null && !diretorioPai.exists()) {
                    diretorioPai.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(arquivoAlvo)) {
                    InputStream inSocket = cliente.getInputStream();
                    long totalRecebido = 0;
                    byte[] buffer = new byte[4096];
                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = (int) Math.min(buffer.length, tamanhoArquivo - totalRecebido);
                        int bytesLidos = inSocket.read(buffer, 0, bytesParaLer);
                        if (bytesLidos == -1) throw new EOFException("Conexão perdida durante upload.");
                        fos.write(buffer, 0, bytesLidos);
                        totalRecebido += bytesLidos;
                    }
                }

                // Envia confirmação para o cliente
                saida.println("OK");
            }
        }
    }

    private void handleDownloadPasta(String nomePastaComId) {
        try {
            File pastaFonte = new File(Servidor.getRoot(), nomePastaComId);
            if (!pastaFonte.exists() || !pastaFonte.isDirectory()) {
                saida.println("550 Pasta não encontrada: " + nomePastaComId);
                return;
            }

            saida.println("150 Iniciando transferência da pasta: " + nomePastaComId);

            List<File> arquivosParaEnviar = new ArrayList<>();
            coletarArquivosRecursivamente(pastaFonte, arquivosParaEnviar);

            for (File arquivo : arquivosParaEnviar) {
                String caminhoRelativo = pastaFonte.toPath().relativize(arquivo.toPath()).toString().replace("\\", "/");

                saida.println("FILE:" + caminhoRelativo);
                saida.println(arquivo.length());

                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    fis.transferTo(cliente.getOutputStream());
                    cliente.getOutputStream().flush();
                }

                String respostaCliente = entrada.readLine();
                if (respostaCliente == null || !respostaCliente.equals("OK")) {
                    throw new IOException("Confirmação do cliente para o arquivo " + caminhoRelativo + " falhou.");
                }
            }

            saida.println("END_FOLDER");
            saida.println("226 Download concluído");
        } catch (Exception e) {
            System.err.println("Erro crítico durante o download no servidor: " + e.getMessage());
        }
    }

    private void coletarArquivosRecursivamente(File pasta, List<File> arquivos) {
        File[] conteudo = pasta.listFiles();
        if (conteudo == null) return;
        Arrays.sort(conteudo, Comparator.comparing(File::getName));
        for (File item : conteudo) if (item.isFile()) arquivos.add(item);
        for (File item : conteudo) if (item.isDirectory()) coletarArquivosRecursivamente(item, arquivos);
    }

    private void deletarPasta(File pasta) {
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
            if (cliente != null && !cliente.isClosed()) {
                cliente.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Servidor servidor = new Servidor();
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::stop));
        servidor.start();
    }
}