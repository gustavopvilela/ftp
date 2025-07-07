# Trabalho de Redes de Computadores I - Simulação de FTP: Sincronização de pastas de arquivos no servidor via FTP

![Redes](https://img.shields.io/badge/IFMG-Redes%20de%20Computadores%20I-e47200)  [![Java](https://img.shields.io/badge/Java-21.0.6-e69b00)](https://www.java.com/)

## Introdução

Este projeto, desenvolvido pelos alunos Gustavo Henrique Pereira Vilela e Iasmim Garcia Castro, implementa uma aplicação de FTP (File Transfer Protocol) para a transferência confiável de arquivos em rede. Utilizando a arquitetura cliente/servidor, a aplicação permite a sincronização completa de pastas entre um cliente e um servidor, com o servidor sendo capaz de gerenciar múltiplas conexões de clientes simultaneamente.

A aplicação conta com interfaces gráficas (GUI) tanto para o cliente quanto para o servidor, facilitando a operação e o monitoramento das transferências.

## Funcionalidades

### Servidor
- **Interface Gráfica de Gerenciamento**: Painel para iniciar, parar e monitorar a atividade do servidor.
- **Suporte a Múltiplos Clientes**: Utiliza um pool de threads para atender várias conexões de clientes de forma concorrente.
- **Porta Configurável**: Permite ao usuário definir a porta em que o servidor irá operar.
- **Log de Atividades**: Exibe um log detalhado e colorido em tempo real, registrando conexões, transferências, erros e outros eventos importantes.
- **Gerenciamento de Pastas**:
    - Armazena as pastas enviadas em um diretório raiz (`root`).
    - Lista as pastas disponíveis para os clientes, exibindo ID, nome e tamanho.
    - Comprime pastas em formato ZIP para otimizar o processo de download.

### Cliente
- **Interface Gráfica Intuitiva**: Facilita a conexão com o servidor e a transferência de arquivos.
- **Configuração e Teste de Conexão**: Permite configurar o host e a porta do servidor e testar a conexão antes de realizar operações.
- **Sincronização de Pastas**:
    - **Upload**: Seleciona uma pasta local e a envia para o servidor. Um ID exclusivo é gerado para cada pasta para evitar conflitos.
    - **Download**: Baixa uma pasta completa do servidor para um local escolhido pelo usuário.
- **Visualização de Arquivos no Servidor**: Exibe uma lista de todas as pastas no servidor, com detalhes como ID, nome e tamanho formatado.
- **Log de Operações**: Registra todas as ações do cliente, desde a seleção de pastas até o status de cada transferência, com mensagens coloridas para fácil identificação.

## Tecnologias Utilizadas

- **Java 21**: A aplicação é desenvolvida utilizando a versão 21 da linguagem Java.
- **Java Swing**: Para a construção das interfaces gráficas do cliente e do servidor.
- **Maven**: Para gerenciamento de dependências e automação do build do projeto.

## Requisitos

- **Java Development Kit (JDK)**: Versão 21 ou superior.
- **Maven**: Versão 3.6 ou superior para compilar o projeto.

## Como Compilar e Executar

O projeto utiliza o Maven para facilitar a compilação e o empacotamento. Siga os passos abaixo:

1.  **Clone o repositório** (ou certifique-se de estar na pasta raiz do projeto).

2.  **Compile o projeto** usando o Maven. Este comando irá baixar as dependências e criar um arquivo `.jar` executável na pasta `target/`.
    ```sh
    mvn clean install
    ```

3.  **Execute a aplicação**:
    ```sh
    java -jar target/ftp-1.0-SNAPSHOT.jar
    ```

4.  Após a execução, uma janela de seleção aparecerá, permitindo que você inicie a aplicação em modo **Servidor** ou **Cliente**.

## Como Utilizar

### Modo Servidor

1.  Após iniciar a aplicação, clique no botão **"Servidor"**.
2.  A janela "Painel de Controle do Servidor FTP" será aberta.
3.  Você pode alterar a porta padrão (12381), se desejar.
4.  Clique em **"Iniciar Servidor"**. O log de atividades mostrará que o servidor está online e pronto para aceitar conexões.
5.  Para encerrar, clique em **"Parar Servidor"** ou feche a janela (será pedida uma confirmação).

### Modo Cliente

1.  Inicie a aplicação e clique no botão **"Cliente"**.
2.  Na janela "Cliente FTP", insira o endereço de IP (ou hostname) e a porta do servidor.
3.  Clique em **"Testar conexão"** para garantir que o cliente consegue se comunicar com o servidor. Uma mensagem de sucesso será exibida.
4.  Após conectar, o botão **"Atualizar lista de pastas"** pode ser usado para ver os diretórios já existentes no servidor.

#### Para fazer Upload:

1.  Clique em **"Selecionar Pasta"** e escolha o diretório que deseja enviar.
2.  O nome da pasta e um ID gerado para ela aparecerão na área de status.
3.  Clique em **"Enviar para o servidor"**. O progresso será exibido no log de atividades.

#### Para fazer Download:

1.  Na tabela "Pastas no servidor", selecione a pasta que deseja baixar.
2.  Clique no botão **"Baixar pasta selecionada"**.
3.  Uma janela se abrirá para que você escolha o local onde a pasta será salva.

## Estrutura do Projeto

```
.
└── src
  └── main
    └── java
      ├── ftp
      │   ├── cliente      # Contém a lógica e a GUI do cliente
      │   ├── servidor     # Contém a lógica e a GUI do servidor
      │   └── Main.java    # Ponto de entrada da aplicação
      ├── styles           # Classes para estilização da GUI
      └── utils            # Utilitários, como o gerador de ID de pastas
```
