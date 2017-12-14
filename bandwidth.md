## Android Profiler

O consumo de banda de rede se mostrou satisfatório em poucos momentos, superando 1MB/s de download e chegando a um pico de 1.5MB/s, mas na maioria das vezes permaneceu abaixo dos 0.5MB/s (média de 200kbps) tornando o download demorado. Já era esperado visto que todo trabalho todo recai sobre 1 thread apenas. O desempenho portanto fica limitado a uma alta frequência de requisições de rede e baixa velocidade de download.

##  Boas práticas utilizadas

O simples fato de fazer caching dos episódios no primeiro download já é uma otimização considerável, visto que não será necessário tá sempre fazendo requisições HTTP para baixar o mesmo conteúdo quando não há atualização disponível para lista.

`MainActivity.java`

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    {...}

    if (hasInternetConnection()) {
        new DownloadXmlTask().execute(RSS_FEED);
        print("Checking update...", Toast.LENGTH_LONG);
    } else new RestoreFeedList().execute();

    {...}
}
```