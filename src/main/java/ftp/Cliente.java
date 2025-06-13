package ftp;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Cliente extends JFrame {
    private static final String HOST = "26.134.36.244";
    private static final int PORTA = 12381;

    /* Componentes da interface */
    private JLabel statusLabel;
    private JButton uploadButton;
    private JList<String> pastasServidorList;
    private DefaultListModel<String> listModel;
    private JButton downloadButton;
    private JTextArea logArea;

    /* Estado da aplicação */
    private File pastaSelecionada;
    private String pastaSelecionadaId;

    public Cliente() {
        inicializarGUI();
        atualizarPastasServidor();
    }

    private void inicializarGUI() {
        setTitle("Cliente FTP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel uploadPanel = createUploadPanel();
        JPanel servidorPanel = createServidorPanel();
        JPanel logPanel = createLogPanel();
        JPanel statusPanel = createStatusPanel();

        add(uploadPanel, BorderLayout.NORTH);
        add(servidorPanel, BorderLayout.CENTER);
        add(logPanel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    private JPanel createUploadPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Upload de pasta"));

        JButton selecionarPastaButton = new JButton("Selecionar Pasta");
        selecionarPastaButton.addActionListener(this::selecionarPasta);

        uploadButton = new JButton("Enviar para o servidor");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(this::uploadPasta);

        panel.add(selecionarPastaButton);
        panel.add(uploadButton);

        return panel;
    }

    private JPanel createServidorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Pastas no servidor"));

        /* Lista de pastas no servidor */
        listModel = new DefaultListModel<>();
        pastasServidorList = new JList<>(listModel);
        pastasServidorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(pastasServidorList);
        scrollPane.setPreferredSize(new Dimension(400, 200));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton refreshButton = new JButton("Atualizar lista");
        refreshButton.addActionListener(e -> atualizarPastasServidor());

        downloadButton = new JButton("Baixar pasta selecionada");
        downloadButton.addActionListener(this::downloadPasta);

        buttonPanel.add(refreshButton);
        buttonPanel.add(downloadButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Log de atividade"));

        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Pronto para uso");
        panel.add(statusLabel);
        return panel;
    }

    private void selecionarPasta(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecione a pasta para sincronizar");

        int resultado = chooser.showOpenDialog(this);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            pastaSelecionada = chooser.getSelectedFile();
            pastaSelecionadaId = gerarIdDaPasta(pastaSelecionada);

            uploadButton.setEnabled(true);
            statusLabel.setText("Pasta selecionada: " + pastaSelecionada.getName());

            gerarMensagemLog("[" + LocalDateTime.now() + "] Pasta selecionada: " + pastaSelecionada.getAbsolutePath());
            gerarMensagemLog("[" + LocalDateTime.now() + "] ID gerado para a pasta: " + pastaSelecionadaId);
        }
    }

    private String gerarIdDaPasta (File pasta) {
        try {
            String caminho = pasta.getAbsolutePath();
            long ultimaModificacao = pasta.lastModified();
            long tamanho = calcularTamanhoPasta(pasta);

            int hash = (caminho + ultimaModificacao + tamanho).hashCode();
            return String.valueOf(Math.abs(hash));
        }
        catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private long calcularTamanhoPasta (File pasta) {
        long tamanho = 0;
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    tamanho += calcularTamanhoPasta(arquivo);
                }
                else {
                    tamanho += arquivo.length();
                }
            }
        }

        return tamanho;
    }

    private void uploadPasta(ActionEvent e) {
        if (pastaSelecionada == null) {
            mostrarErro("Nenhuma pasta selecionada");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            uploadButton.setEnabled(false);
            statusLabel.setText("Enviando pasta...");
        });

        new Thread(() -> {
            try {
                executarUpload();
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta enviada com sucesso.");
                    atualizarPastasServidor();
                    uploadButton.setEnabled(true);
                    statusLabel.setText("Upload concluído.");
                });
            }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no upload: " + ex.getMessage());
                    uploadButton.setEnabled(true);
                    statusLabel.setText("Erro no upload.");
                });
                gerarMensagemLog("[" + LocalDateTime.now() + "] ERRO: " + ex.getMessage());
            }
        }).start();
    }

    private void executarUpload() {
        gerarMensagemLog("[" + LocalDateTime.now() + "] Iniciando conexão com o servidor.");

        try (Socket socket = new Socket(HOST, PORTA);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), false)) {

            String resposta = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Servidor: " + resposta);

            String infoPasta = pastaSelecionada.getName() + "|" + pastaSelecionadaId;
            saida.println("CHECK_FOLDER " + infoPasta);
            saida.flush();

            resposta = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Verificação: " + resposta);

            saida.println("UPLOAD_FOLDER " + infoPasta);
            saida.flush();
            resposta = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Servidor: " + resposta);

            if (resposta.startsWith("150")) {
                enviarConteudosPasta(pastaSelecionada, socket, saida, entrada);

                saida.println("END_FOLDER");
                saida.flush();

                resposta = entrada.readLine();
                gerarMensagemLog("[" + LocalDateTime.now() + "] Resultado: " + resposta);

                if (!resposta.startsWith("226")) {
                    throw new Exception("Upload falhou: " + resposta);
                }
            }
            else {
                throw new Exception("Servidor recusou o upload: " + resposta);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* Envia recursivamente todos os conteúdos da pasta */
    private void enviarConteudosPasta (File pasta, Socket socket, PrintWriter saida, BufferedReader entrada) throws Exception {
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) return;

        for (File arquivo : arquivos) {
            if (arquivo.isDirectory()) {
                /* Processa as subpastas primeiro */
                enviarConteudosPasta(arquivo, socket, saida, entrada);
            }
            else {
                /* Calcula o caminho relativo para manter a mesma estrutura */
                String caminhoRelativo = pastaSelecionada.toPath().relativize(arquivo.toPath()).toString();

                gerarMensagemLog("[" + LocalDateTime.now() + "] Enviando: " + caminhoRelativo);

                saida.println("FILE:" + caminhoRelativo);
                saida.flush();
                saida.println(arquivo.length());
                saida.flush();

                /* Envia o conteúdo do arquivo */
                try (FileInputStream fis = new FileInputStream(arquivo)) {
                    byte[] buffer = new byte[1024];
                    int bytesLidos;

                    while ((bytesLidos = fis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesLidos);
                    }

                    socket.getOutputStream().flush();
                }

                /* Aguardando confirmação do servidor */
                String resposta = entrada.readLine();
                if (!"OK".equals(resposta)) {
                    throw new Exception("Erro ao enviar arquivo: " + caminhoRelativo);
                }
            }
        }
    }

    /* Atualiza a lista de pastas que estão disponíveis no servidor */
    private void atualizarPastasServidor () {
        new Thread(() -> {
            try {
                List<String> pastas = getPastasServidor();
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (String pasta : pastas) {
                        listModel.addElement(pasta);
                    }
                    gerarMensagemLog("[" + LocalDateTime.now() + "] Lista de pastas atualizada: " + pastas.size() + " itens");
                });
            }
            catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro ao atualizar lista: " + e.getMessage());
                });
                gerarMensagemLog("[" + LocalDateTime.now() + "] ERRO ao atualizar lista: " + e.getMessage());
            }
        }).start();
    }

    private List<String> getPastasServidor() throws Exception {
        List<String> pastas = new ArrayList<>();

        try (Socket socket = new Socket(HOST, PORTA);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), false)) {

            // 1) Se o seu servidor envia um greeting (220, 200, etc.), leia-o:
            String line = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Greeting: " + line);

            // 2) Peça a lista
            saida.println("LIST");
            saida.flush();

            // 3) Leia o código de início (esperamos algo como "150 ...")
            line = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Resposta LIST início: " + line);
            if (line == null || !line.startsWith("150")) {
                throw new Exception("Não foi possível iniciar listagem de pastas: " + line);
            }

            // 4) Agora, leia tudo até o “226” de fim de listagem
            while ((line = entrada.readLine()) != null) {
                gerarMensagemLog("[" + LocalDateTime.now() + "] Listando: " + line);

                if (line.startsWith("226")) {
                    // terminou
                    break;
                }
                if (line.startsWith("FOLDER:") || line.startsWith("PASTA:")) {
                    String[] parts = line.split(":", 2);
                    pastas.add(parts[1].trim());
                }
            }
        }

        return pastas;
    }


    /* Função para download de pastas no servidor */
    private void downloadPasta (ActionEvent e) {
        String pastaSelecionada = pastasServidorList.getSelectedValue();
        if (pastaSelecionada == null) {
            mostrarErro("Selecione uma pasta para baixar");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha onde salvar a pasta");

        int resultado = chooser.showSaveDialog(this);
        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File localSalvamento = chooser.getSelectedFile();

        downloadButton.setEnabled(false);
        statusLabel.setText("Baixando pasta...");

        new Thread(() -> {
            try {
                executarDownload(pastaSelecionada, localSalvamento);
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta baixada com sucesso.");
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Download concluído");
                });
            }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no download: " + ex.getMessage());
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Erro no download");
                });
                gerarMensagemLog("[" + LocalDateTime.now() + "] ERRO no download: " + ex.getMessage());
            }
        }).start();
    }

    private void executarDownload (String pasta, File localSalvamento) throws Exception {
        gerarMensagemLog("[" + LocalDateTime.now() + "] Iniciando download de: " + pasta);

        /* Pegando o nome original e o ID */
        String nomeOriginal = extrairNomeOriginal(pasta);
        File pastaAlvo = new File(localSalvamento, nomeOriginal);

        /* Garantindo que a pasta alvo existe antes de começar o download */
        if (!pastaAlvo.exists() && !pastaAlvo.mkdirs()) {
            throw new Exception("Não foi possível criar o diretório: " + pastaAlvo.getAbsolutePath());
        }

        try (Socket socket = new Socket(HOST, PORTA);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), false)) {

            entrada.readLine();

            saida.println("DOWNLOAD_FOLDER " + pasta);
            saida.flush();

            String resposta = entrada.readLine();
            gerarMensagemLog("[" + LocalDateTime.now() + "] Servidor: " + resposta);

            if (!resposta.startsWith("150")) {
                throw new Exception("Servidor recusou o download: " + resposta);
            }

            //receberArquivos(pasta, socket, entrada, saida);
            receberArquivos(pastaAlvo, socket, entrada, saida);

            resposta = entrada.readLine(); // END_FOLDER
            resposta = entrada.readLine(); // 226 Download concluído
            gerarMensagemLog("[" + LocalDateTime.now() + "] Download finalizado: " + resposta);
        }
    }

    private String extrairNomeOriginal (String pasta) {
        int ultimoUnderline = pasta.lastIndexOf("_");
        if (ultimoUnderline > 0) {
            return pasta.substring(0, ultimoUnderline);
        }
        return pasta;
    }

    /* Função para receber os arquivos do servidor durante o download */
    private void receberArquivos(File pastaDestino, Socket socket, BufferedReader entrada, PrintWriter saida) throws Exception {
        String linha;

        while ((linha = entrada.readLine()) != null) {
            if (linha.equals("END_FOLDER")) {
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivo = linha.substring(5);
                long tamanhoArquivo = Long.parseLong(entrada.readLine());

                gerarMensagemLog("[" + LocalDateTime.now() + "] Recebendo: " + nomeArquivo + " (" + tamanhoArquivo + " bytes)");

                // Criando o arquivo alvo dentro da pasta de destino
                File arquivoAlvo = new File(pastaDestino, nomeArquivo);

                // CORREÇÃO PRINCIPAL: Verificação segura antes de chamar mkdirs()
                File diretorioPai = arquivoAlvo.getParentFile();
                if (diretorioPai != null && !diretorioPai.exists()) {
                    if (!diretorioPai.mkdirs()) {
                        throw new Exception("Não foi possível criar o diretório: " + diretorioPai.getAbsolutePath());
                    }
                }

                // Recebendo o conteúdo do arquivo
                try (FileOutputStream fos = new FileOutputStream(arquivoAlvo)) {
                    byte[] buffer = new byte[1024];
                    long totalRecebido = 0;

                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = (int)Math.min(buffer.length, tamanhoArquivo - totalRecebido);
                        int bytesLidos = socket.getInputStream().read(buffer, 0, bytesParaLer);

                        if (bytesLidos == -1) {
                            throw new Exception("Conexão perdida durante o download do arquivo: " + nomeArquivo);
                        }

                        fos.write(buffer, 0, bytesLidos);
                        totalRecebido += bytesLidos;
                    }
                }

                saida.println("OK");
                saida.flush();
            }
        }
    }

    private void gerarMensagemLog (String mensagem) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(mensagem + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void mostrarErro (String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
        gerarMensagemLog("[" + LocalDateTime.now() + "] ERRO: " + mensagem);
    }

    private void mostrarSucesso (String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        gerarMensagemLog("[" + LocalDateTime.now() + "] SUCESSO: " + mensagem);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new Cliente().setVisible(true);
        });
    }
}
