# Trabalho de Redes de Computadores I - Simulação de FTP: _Sincronização de pastas de arquivos no servidor via FTP_

## Introdução

Este projeto, feito pelos alunos Gustavo Henrique Pereira Vilela e Iasmim Garcia Castro, implementa uma aplicação de FTP (*File Transfer Protocol*) para transferência confiável de arquivos em rede, seguindo a arquitetura *cliente/servidor*, de acordo com a **RFC959**. O servidor é capaz de atender múltiplos clientes simultaneamente.

## Requisitos de *hardware* e ambiente

- Ao menos duas máquinas (um servidor e clientes) em rede local (computadores do laboratório de redes para testes) ou não;
- Sistemas operacionais Windows ou distribuições Linux (a usada para testes é a Ubuntu 22.04 LTS);
- Java:
  - **Java** 21.0.6 ou superior;
  - **Javac** 21.0.6 ou superior;
  - **Java SE Runtime Environment** (build 21.0.6+8-LTS-188)
  - **Java HotSpot 64-Bit Server VM** (build 21.0.6+8-LTS-188)

## Como compilar

### Caso o computador seja o servidor

1. Navegue até a raiz do projeto `src/main/java/ftp`;
2. Compile o arquivo `ClienteHandler.java` com o comando `javac ClienteHandler.java`;
3. Execute o arquivo gerado com este comando `java ClienteHandler`;
4. **Observações:**
   - O endereço de host que o cliente irá se conectar é o IP da máquina que está rodando o servidor;
   - A porta que será usada para conexão pode ser alterada no arquivo `Servidor.java` (variável `PORTA`);
   - Caso queria, é possível mudar o nome da pasta raiz do servidor no arquivo `Servidor.java` na variável `ROOT`.

### Caso o computador seja o cliente

1. Navegue até a raiz do projeto `src/main/java/ftp`;
2. Compile o arquivo `Cliente.java` com o comando `javac Cliente.java`;
3. Execute o arquivo gerado com este comando `java Cliente`;

## Utilizando a aplicação como cliente

Antes de realizar qualquer ação, é necessário se conectar ao servidor. Para isso, no menu mais acima, há os campos *Servidor* e *Porta*, com os valores padrão `localhost` e `12381`, referenciando a própria máquina.

Dessa forma, troque os valores para o endereço IP ou nome do servidor e a porta que será usada para conexão e clique no botão `Testar conexão`. Esse passo é crucial, uma vez que com ele serão realizadas as configurações iniciais de conexão.

Uma vez conectado, uma mensagem de sucesso aparecerá na tela e, caso o servidor já possua algum arquivo, eles aparecerão na seção *Pastas no servidor*.

Para realizar o upload de pastas, é necessário ter uma conexão bem-sucedida, obrigatoriamente. Então, basta clicar no botão `Selecionar pasta` e escolher o diretório que deseja enviar. É importante ficar atento ao canto inferior esquerdo da janela da aplicação principal, lá estarão mensagens de confirmação importantes para saber o estado da aplicação.

Tendo escolhido a pasta, aperte o botão `Enviar para o servidor`. O processo de envio está todo na seção *Log de atividade*, no qual aparecerão o estado de envio de todos os arquivos. Uma vez finalizado, uma mensagem de sucesso aparecerá na tela. Em caso de erro, uma mensagem correspondente aparecerá demonstrando o ocorrido.