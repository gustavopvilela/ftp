package ftp.cliente;

import utils.FolderIdUtil;
import styles.CustomTableCellRenderer;
import styles.CustomTableHeaderRenderer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Cliente extends JFrame {
    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA_PADRAO = 12381;

    /* --- Componentes da Interface --- */
    private JLabel statusLabel;
    private JButton uploadButton;
    private JButton downloadButton;
    private JTextPane logPane; // Trocado para JTextPane
    private JTable pastasServidorTable; // Trocado para JTable
    private DefaultTableModel tableModel;

    private JTextField hostField;
    private JTextField portaField;
    private JButton conectarButton;
    private JLabel conexaoStatusLabel;

    private JButton selecionarPastaButton;
    private JButton refreshButton;

    /* --- Estado da Aplicação --- */
    private File pastaSelecionada;
    private String pastaSelecionadaId;
    private String hostAtual;
    private int portaAtual;
    private boolean conectado = false;

    /* --- Utilidades --- */
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss:SSS");
    private final Color COR_ERRO = new Color(210, 4, 45);
    private final Color COR_SUCESSO = new Color(0, 128, 0);
    private final Color COR_INFO = new Color(0, 100, 200);
    private final Color COR_PADRAO = Color.BLACK;
    private final Color COR_TIMESTAMP = Color.GRAY;

    public Cliente() {
        hostAtual = HOST_PADRAO;
        portaAtual = PORTA_PADRAO;
        inicializarGUI();
    }

    public void inicializarGUI() {
        setTitle("Cliente FTP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel conexaoPanel = createConexaoPanel();
        JPanel uploadPanel = createUploadPanel(); // Este painel agora incluirá o status
        JPanel servidorPanel = createServidorPanel();
        JPanel logPanel = createLogPanel();
        // A linha que criava o statusPanel foi removida daqui.

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(conexaoPanel);
        topPanel.add(uploadPanel);

        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, servidorPanel, logPanel);
        centerSplitPane.setResizeWeight(0.65);

        add(topPanel, BorderLayout.NORTH);
        add(centerSplitPane, BorderLayout.CENTER);
        // A linha que adicionava o statusPanel ao SOUTH foi removida.

        setSize(1200, 700);
        setLocationRelativeTo(null);

        atualizarEstadoControles();
    }

    private JPanel createConexaoPanel() {
        //... (Este método não muda)
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Configuração do servidor"));

        panel.add(new JLabel("Servidor:"));
        hostField = new JTextField(hostAtual, 15);
        hostField.setToolTipText("Digite o IP do servidor FTP");
        panel.add(hostField);

        panel.add(new JLabel("Porta:"));
        portaField = new JTextField(String.valueOf(portaAtual), 6);
        portaField.setToolTipText("Digite a porta do servidor FTP");
        panel.add(portaField);

        conectarButton = new JButton("Testar conexão");
        conectarButton.addActionListener(this::testarConexao);
        conectarButton.setToolTipText("Testar conexão com o servidor e aplicar configurações");
        panel.add(conectarButton);

        conexaoStatusLabel = new JLabel("Não conectado");
        conexaoStatusLabel.setForeground(Color.RED);
        panel.add(conexaoStatusLabel);

        return panel;
    }

    private void testarConexao(ActionEvent e) {
        //... (Este método não muda)
        String novoHost = hostField.getText().trim();
        String portaTexto = portaField.getText().trim();

        if (novoHost.isEmpty()) {
            mostrarErro("Por favor, digite o endereço do servidor");
            hostField.requestFocus();
            return;
        }

        int novaPorta;
        try {
            novaPorta = Integer.parseInt(portaTexto);
            if (novaPorta < 1 || novaPorta > 65535) {
                throw new NumberFormatException("Porta fora dos limites");
            }
        }
        catch (NumberFormatException ex) {
            mostrarErro("Porta inválida. Digite um número entre 1 e 65535");
            portaField.requestFocus();
            return;
        }

        conectarButton.setEnabled(false);
        conexaoStatusLabel.setText("Testando...");
        conexaoStatusLabel.setForeground(Color.ORANGE);

        new Thread(() -> {
            boolean sucesso = false;
            String mensagemErro = "";

            try {
                gerarMensagemLog("Testando conexão com " + novoHost + ": " + novaPorta, COR_INFO);
                try (Socket socket = new Socket(novoHost, novaPorta)) {
                    BufferedReader entrada =  new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    socket.setSoTimeout(50000);
                    String resposta = entrada.readLine();
                    if (resposta != null) {
                        sucesso = true;
                        gerarMensagemLog("Conexão bem-sucedida. Resposta: " + resposta, COR_SUCESSO);
                    }
                }
            }
            catch (IOException ex) {
                mensagemErro = ex.getMessage();
                gerarMensagemLog("Erro na conexão: " + ex.getMessage(), COR_ERRO);
            }

            final boolean conexaoSucesso = sucesso;
            final String erro = mensagemErro;

            SwingUtilities.invokeLater(() -> {
                conectarButton.setEnabled(true);
                if (conexaoSucesso) {
                    hostAtual = novoHost;
                    portaAtual = novaPorta;
                    conectado = true;
                    conexaoStatusLabel.setText("Conectado (" + hostAtual + ":" + portaAtual + ")");
                    conexaoStatusLabel.setForeground(new Color(0, 128, 0));
                    mostrarSucesso("Conexão estabelecida com sucesso!\nServidor: " + hostAtual + ":" + portaAtual);
                    atualizarPastasServidor();
                } else {
                    conectado = false;
                    conexaoStatusLabel.setText("Erro na conexão");
                    conexaoStatusLabel.setForeground(Color.RED);
                    mostrarErro("Não foi possível conectar ao servidor.\nDetalhes: " + erro);
                }
                atualizarEstadoControles();
            });
        }).start();
    }

    private void atualizarEstadoControles () {
        // Habilita/desabilita botões com base no status da conexão
        selecionarPastaButton.setEnabled(conectado);
        downloadButton.setEnabled(conectado);
        refreshButton.setEnabled(conectado);

        // O botão de upload depende da conexão E de uma pasta selecionada
        uploadButton.setEnabled(conectado && pastaSelecionada != null);

        // Atualiza o título da janela
        if (conectado) {
            setTitle("Cliente FTP - Conectado a " + hostAtual + ":" + portaAtual);
        } else {
            setTitle("Cliente FTP - Não conectado");
        }
    }

    private JPanel createUploadPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Upload de pasta"));

        // A variável agora é um campo da classe
        selecionarPastaButton = new JButton("Selecionar Pasta");
        selecionarPastaButton.addActionListener(this::selecionarPasta);

        uploadButton = new JButton("Enviar para o servidor");
        // O estado inicial será definido em atualizarEstadoControles()
        uploadButton.addActionListener(this::uploadPasta);

        panel.add(selecionarPastaButton);
        panel.add(uploadButton);

        panel.add(Box.createHorizontalStrut(20));

        statusLabel = new JLabel("Pronto para uso");
        statusLabel.setForeground(new Color(80, 80, 80));
        panel.add(statusLabel);

        return panel;
    }

    /* --- PAINEL DO SERVIDOR (COM JTABLE) --- */
    private JPanel createServidorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Pastas no servidor"));

        String[] colunas = {"ID", "Nome da Pasta", "Tamanho"};
        tableModel = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        pastasServidorTable = new JTable(tableModel);
        pastasServidorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pastasServidorTable.setFillsViewportHeight(true);
        pastasServidorTable.getTableHeader().setReorderingAllowed(false);

        // --- INÍCIO DA ESTILIZAÇÃO ---

        // 1. Estilizar o cabeçalho da tabela
        JTableHeader header = pastasServidorTable.getTableHeader();
        header.setDefaultRenderer(new CustomTableHeaderRenderer(pastasServidorTable));
        header.setFont(new Font("Arial", Font.BOLD, 12));

        // 2. Aumentar a altura da linha para criar padding vertical
        pastasServidorTable.setRowHeight(28);

        // 3. Aplicar o renderizador personalizado para padding e alinhamento nas células
        CustomTableCellRenderer cellRenderer = new CustomTableCellRenderer();
        pastasServidorTable.setDefaultRenderer(Object.class, cellRenderer);

        // 4. Ajustar largura das colunas
        pastasServidorTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        pastasServidorTable.getColumnModel().getColumn(1).setPreferredWidth(280);
        pastasServidorTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        // --- FIM DA ESTILIZAÇÃO ---

        JScrollPane scrollPane = new JScrollPane(pastasServidorTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        refreshButton = new JButton("Atualizar lista de pastas");
        refreshButton.addActionListener(e -> {
            if (conectado) {
                atualizarPastasServidor();
            }
            else {
                mostrarErro("Conecte-se ao servidor primeiro!");
            }
        });

        downloadButton = new JButton("Baixar pasta selecionada");
        downloadButton.addActionListener(this::downloadPasta);

        buttonPanel.add(refreshButton);
        buttonPanel.add(downloadButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    /* --- PAINEL DE LOG (COM JTEXTPANE) --- */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Log de Atividade"));

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Arial", Font.BOLD, 12));
        SimpleAttributeSet paragraphStyle = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraphStyle, 0.5f);
        StyleConstants.setBold(paragraphStyle, true);
        logPane.setParagraphAttributes(paragraphStyle, true);

        JScrollPane scrollPane = new JScrollPane(logPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

//    private JPanel createStatusPanel() {
//        //... (Este método não muda)
//        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
//        statusLabel = new JLabel("Pronto para uso");
//        panel.add(statusLabel);
//        return panel;
//    }

    private String gerarIdDaPasta(File pasta) throws IOException {
        return FolderIdUtil.obterId(pasta);
    }

    private void selecionarPasta(ActionEvent e) throws UncheckedIOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecione a pasta para sincronizar");

        int resultado = chooser.showOpenDialog(this);

        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            pastaSelecionada = chooser.getSelectedFile();
            pastaSelecionadaId = gerarIdDaPasta(pastaSelecionada);

            statusLabel.setText("Pasta selecionada: " + pastaSelecionada.getName());

            gerarMensagemLog("Pasta selecionada: " + pastaSelecionada.getAbsolutePath() + " | Pronto para enviar para o servidor.", COR_PADRAO);
            gerarMensagemLog("ID gerado para a pasta: " + pastaSelecionadaId, COR_PADRAO);

            // ATUALIZA O ESTADO DOS CONTROLES AQUI TAMBÉM
            // para habilitar o botão de upload após selecionar uma pasta
            atualizarEstadoControles();

        } catch (IOException ex) {
            mostrarErro("Erro ao selecionar pasta: " + ex.getMessage());
        }
    }

    private void uploadPasta(ActionEvent e) {
        //... (Este método não muda)
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
                    statusLabel.setText("Upload concluído. | O sistema está pronto para uso.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no upload: " + ex.getMessage());
                    uploadButton.setEnabled(true);
                    statusLabel.setText("Erro no upload. | Tente novamente.");
                });
                gerarMensagemLog("ERRO: " + ex.getMessage(), COR_ERRO);
            }
        }).start();
    }

    private void executarUpload() {
        gerarMensagemLog("=== INICIANDO UPLOAD ===", COR_INFO);
        // ... Lógica de upload não muda, mas chamadas de log agora podem ter cor
        gerarMensagemLog("Conectando ao servidor " + hostAtual + ":" + portaAtual, COR_INFO);

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000);
            gerarMensagemLog("Socket configurado - TCP_NODELAY: true, SO_TIMEOUT: 30s", COR_PADRAO);

            String resposta = entrada.readLine();
            gerarMensagemLog("SERVIDOR (conexão): " + resposta, COR_PADRAO);

            String infoPasta = pastaSelecionada.getName() + "|" + pastaSelecionadaId;
            gerarMensagemLog("ENVIANDO: UPLOAD_FOLDER " + infoPasta, COR_INFO);
            saida.println("UPLOAD_FOLDER " + infoPasta);

            resposta = entrada.readLine();
            gerarMensagemLog("SERVIDOR (upload): " + resposta, COR_PADRAO);

            if (resposta.startsWith("150")) {
                List<File> todosArquivos = coletarTodosArquivos(pastaSelecionada);
                enviarArquivosComConfirmacao(todosArquivos, socket, saida, entrada);

                saida.println("END_FOLDER");
                resposta = entrada.readLine();
                gerarMensagemLog("SERVIDOR (fim): " + resposta, COR_PADRAO);

                if (!resposta.startsWith("226")) {
                    throw new Exception("Upload falhou - resposta final: " + resposta);
                }
                gerarMensagemLog("=== UPLOAD CONCLUÍDO COM SUCESSO ===", COR_SUCESSO);
            } else {
                throw new Exception("Servidor recusou o upload: " + resposta);
            }
        } catch (Exception e) {
            gerarMensagemLog("ERRO CRÍTICO: " + e.getClass().getSimpleName() + " - " + e.getMessage(), COR_ERRO);
            throw new RuntimeException(e);
        }
    }

    private List<File> coletarTodosArquivos(File pastaRaiz) {
        //... (Este método não muda)
        List<File> arquivos = new ArrayList<>();
        coletarArquivosRecursivamente(pastaRaiz, arquivos);
        arquivos.sort(Comparator.comparing(f -> pastaRaiz.toPath().relativize(f.toPath()).toString()));
        return arquivos;
    }

    private void coletarArquivosRecursivamente(File pasta, List<File> arquivos) {
        //... (Este método não muda)
        File[] conteudo = pasta.listFiles();
        if (conteudo == null) return;
        Arrays.sort(conteudo, Comparator.comparing(File::getName));
        for (File item : conteudo) if (item.isFile()) arquivos.add(item);
        for (File item : conteudo) if (item.isDirectory()) coletarArquivosRecursivamente(item, arquivos);
    }

    private void enviarArquivosComConfirmacao(List<File> arquivos, Socket socket, PrintWriter saida, BufferedReader entrada) throws Exception {
        //... (Este método não muda, mas o log terá cores)
        int totalArquivos = arquivos.size();
        for (int i = 0; i < totalArquivos; i++) {
            File arquivo = arquivos.get(i);
            String caminhoRelativo = pastaSelecionada.toPath().relativize(arquivo.toPath()).toString();
            final int arquivoAtual = i + 1;
            SwingUtilities.invokeLater(() -> statusLabel.setText("Enviando %d/%d: %s".formatted(arquivoAtual, totalArquivos, caminhoRelativo)));

            saida.println("FILE:" + caminhoRelativo);
            saida.println(arquivo.length());

            try (FileInputStream fis = new FileInputStream(arquivo)) {
                fis.transferTo(socket.getOutputStream());
            }

            String resposta = entrada.readLine();
            if (!"OK".equals(resposta)) {
                throw new Exception("Erro ao enviar arquivo " + caminhoRelativo);
            }
        }
    }

    /* --- ATUALIZAÇÃO DA TABELA DE PASTAS --- */
    private void atualizarPastasServidor() {
        new Thread(() -> {
            try {
                // Limpa a tabela antes de buscar novos dados
                SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));

                List<String[]> pastas = getPastasServidor();
                SwingUtilities.invokeLater(() -> {
                    for (String[] pasta : pastas) {
                        tableModel.addRow(pasta);
                    }
                    gerarMensagemLog("Lista de pastas atualizada: " + pastas.size() + " itens.", COR_INFO);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> mostrarErro("Erro ao atualizar lista: " + e.getMessage()));
                gerarMensagemLog("ERRO ao atualizar lista: " + e.getMessage(), COR_ERRO);
            }
        }).start();
    }

    private List<String[]> getPastasServidor() throws Exception {
        List<String[]> pastas = new ArrayList<>();

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            entrada.readLine(); // "220 Servidor Pronto"
            saida.println("LIST");

            String line = entrada.readLine();
            if (line == null || !line.startsWith("150")) {
                throw new Exception("Não foi possível iniciar listagem: " + line);
            }

            while ((line = entrada.readLine()) != null) {
                if (line.startsWith("226")) {
                    break; // Fim da lista
                }
                if (line.startsWith("PASTA_INFO:")) {
                    String[] parts = line.substring(11).split("\\|", 3);
                    if (parts.length == 3) {
                        String id = parts[0];
                        String nome = parts[1];
                        String tamanho = formatarTamanho(Long.parseLong(parts[2]));
                        pastas.add(new String[]{id, nome, tamanho});
                    }
                }
            }
        }
        return pastas;
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /* --- DOWNLOAD DE PASTA (usando a JTable) --- */
    private void downloadPasta(ActionEvent e) {
        int selectedRow = pastasServidorTable.getSelectedRow();
        if (selectedRow == -1) {
            mostrarErro("Selecione uma pasta na tabela para baixar.");
            return;
        }

        // Constrói o nome da pasta no formato que o servidor espera: nome_id
        String id = (String) tableModel.getValueAt(selectedRow, 0);
        String nome = (String) tableModel.getValueAt(selectedRow, 1);
        String pastaServidor = nome + "_" + id;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha onde salvar a pasta '" + nome + "'");

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File localSalvamento = chooser.getSelectedFile();
        downloadButton.setEnabled(false);
        statusLabel.setText("Baixando pasta...");

        new Thread(() -> {
            try {
                executarDownload(pastaServidor, localSalvamento);
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta '" + nome + "' baixada com sucesso.");
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Download concluído. | O sistema está pronto para uso.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no download: " + ex.getMessage());
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Erro no download. | Tente novamente.");
                });
                gerarMensagemLog("ERRO no download: " + ex.getMessage(), COR_ERRO);
            }
        }).start();
    }

    private void executarDownload(String pasta, File localSalvamento) throws Exception {
        //... (Este método não muda)
        gerarMensagemLog("=== INICIANDO DOWNLOAD ===", COR_INFO);
        String nomeOriginal = FolderIdUtil.extrairNomeOriginal(pasta);
        File pastaAlvo = new File(localSalvamento, nomeOriginal);
        localSalvamento.mkdirs();

        try (Socket socket = new Socket(hostAtual, portaAtual)) {
            socket.setSoTimeout(180000);
            PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            entrada.readLine(); // Greeting
            saida.println("DOWNLOAD_FOLDER " + pasta);

            String resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("150")) {
                throw new IOException("Servidor recusou o download: " + resposta);
            }
            gerarMensagemLog("Servidor: " + resposta, COR_INFO);
            statusLabel.setText("Recebendo stream de dados...");

            try (ZipInputStream zis = new ZipInputStream(socket.getInputStream())) {
                ZipEntry zipEntry;
                byte[] buffer = new byte[8192];
                while ((zipEntry = zis.getNextEntry()) != null) {
                    File novoArquivo = new File(pastaAlvo, zipEntry.getName());
                    if (zipEntry.isDirectory()) {
                        novoArquivo.mkdirs();
                    } else {
                        novoArquivo.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(novoArquivo)) {
                            int bytesLidos;
                            while ((bytesLidos = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesLidos);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }
            gerarMensagemLog("=== DOWNLOAD CONCLUÍDO COM SUCESSO ===", COR_SUCESSO);
        }
    }

    /* --- MÉTODOS DE LOG COLORIDO --- */
    /**
     * Adiciona uma string ao JTextPane com uma cor específica, respeitando a fonte do painel.
     * @param texto A mensagem a ser adicionada.
     * @param cor A cor do texto.
     */
    private void appendColorido(String texto, Color cor) {
        StyledDocument doc = logPane.getStyledDocument();

        // Cria um conjunto de atributos para o estilo do texto
        SimpleAttributeSet style = new SimpleAttributeSet();

        // 1. Define a cor do texto
        StyleConstants.setForeground(style, cor);

        // 2. CORREÇÃO: Define a família, tamanho e estilo da fonte
        // com base na fonte que já está configurada no logPane.
        Font logFont = logPane.getFont();
        StyleConstants.setFontFamily(style, logFont.getFamily());
        StyleConstants.setFontSize(style, logFont.getSize());
        StyleConstants.setBold(style, logFont.isBold());
        StyleConstants.setItalic(style, logFont.isItalic());

        try {
            // Insere o texto no final do documento com o estilo completo
            doc.insertString(doc.getLength(), texto, style);
        } catch (BadLocationException e) {
            // Este erro é improvável de acontecer aqui
            e.printStackTrace();
        }
    }

    private void gerarMensagemLog(String mensagem, Color cor) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = "[" + LocalDateTime.now().format(formatter) + "] ";
            appendColorido(timestamp, COR_TIMESTAMP);
            appendColorido(mensagem + "\n", cor);
            logPane.setCaretPosition(logPane.getDocument().getLength());
        });
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
        gerarMensagemLog("ERRO: " + mensagem.replace("\n", " "), COR_ERRO);
    }

    private void mostrarSucesso(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        gerarMensagemLog("SUCESSO: " + mensagem.replace("\n", " "), COR_SUCESSO);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Erro ao carregar look and feel: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}