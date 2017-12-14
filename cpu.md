## Android Profiler

Pela análise observa-se que o uso de CPU é mínimo (quase nulo) na grande maioria das vezes, chegando a utilizar no máximo 30% quando submetido a alto estresse.
Para a verificação foram realizados:
- abertura de várias descrições de ítens e configurações
- scroll dos ítens por certo período de tempo e com grande intensidade
- downloads simultâneos
- execução dos ítens baixados

## AndroidDevMetrics

Segundo o AndroidDevMetrics há uma ligeira queda de fps mas que não compremete tanto o desempenho do app, principalmente na interação do usuário. A média do 'LifeCycle' ficou por volta de 160ms para a MainActivity.

## Boas práticas utilizadas

Para evitar pontos de lentidão, principalmente em tarefas pesadas, foram utilizados threads de background (AsyncTasks & Services) para tarefas como download e acessos ao banco de dados.

Exemplo de download do RSS_FEED utilizando AsyncTask:

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

Exemplo de download utilizando IntentService:

`XmlFeedAdapter.java`

```java
@Override
public View getView(final int position, View convertView, ViewGroup parent) {
    
    {...}

    // Call download service
    Intent downloadService = new Intent(getContext(), DownloadService.class);
    downloadService.setData(Uri.parse(getItem(position).getDownloadLink()));
    downloadService.putExtra("position", position);
    getContext().startService(downloadService);

    {...}
}
```

Com o objetivo de reduzir ao máximo a frequência de acesso ao banco de dados muitas operações são realizadas simultaneamente.

`MainActivity.java`

```java
private class SaveFeedList extends AsyncTask<List<ItemFeed>, Void, Void> {
    protected final Void doInBackground(List<ItemFeed>... items) {
        {...}
        ContentValues values = new ContentValues();
        values.put(PodcastProviderContract.EPISODE_URI, "");
        for (ItemFeed item : items[0]) {
            if (!dlinks.isEmpty() && dlinks.contains(item.getDownloadLink()))
                continue;
            values.put(PodcastProviderContract.TITLE, item.getTitle());
            values.put(PodcastProviderContract.DESCRIPTION, item.getDescription());
            values.put(PodcastProviderContract.DATE, item.getPubDate());
            values.put(PodcastProviderContract.EPISODE_LINK, item.getLink());
            values.put(PodcastProviderContract.DOWNLOAD_LINK, item.getDownloadLink());
            provider.insert(PodcastProviderContract.EPISODE_LIST_URI, values);
        }
        return null;
    }
    {...}
```

Para melhorar desempenho no uso de CPU foi adicionado aceleração de hardware para gráficos 2D.

`AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="br.ufpe.cin.if710.podcast">
    {...}
    <application
            android:hardwareAccelerated="true"
            {...}
    </application>
</manifest>
```

Evitar alto processamento na thread principal ao utilizar BroadcastReceivers passando o trabalho para threads em background.

`MainActivity.java`

```java
private BroadcastReceiver onEpisodeCompleteEvent = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String fileUri = intent.getStringExtra("fileUri");
        int position = intent.getIntExtra("position", -1);
        new RemoveEpisodeTask().execute(fileUri, String.valueOf(position));
    }
};
```