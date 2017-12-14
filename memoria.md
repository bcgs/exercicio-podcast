## Android Profiler

O app se saiu bem no teste de memória, chegando a consumir não mais que 30MB em situação de estresse. 
Nota-se um crescimento gradual no consumo de memória a medida que um episódio é baixado mas em seguida o garbage collector atua na liberação de recursos para a heap, o que faz com que o consumo matenha uma média baixa.

## LeakCanary

Foi utilizado o LeakCanary para a verificação de vazamento de memória, e mesmo após vários testes nada foi identificado.

Os mesmos testes que foram realizados para o teste de CPU também foram utilizados neste teste de memória.

## Boas práticas utilizadas

Se tem algo que realmente impacta no consumo de memória é a reciclagem de views. É notório a diferença de consumo para grandes listas de ítens.

`XmlFeedAdapter.java`

```java
@Override
public View getView(final int position, View convertView, ViewGroup parent) {
    final ViewHolder holder;
    if (convertView == null) {
        convertView = View.inflate(getContext(), linkResource, null);
        holder = new ViewHolder();
        holder.item_title = convertView.findViewById(R.id.item_title);
        holder.item_date = convertView.findViewById(R.id.item_date);
        holder.item_action = convertView.findViewById(R.id.item_action);
        convertView.setTag(holder);
    } else {
        holder = (ViewHolder) convertView.getTag();
    }
    {...}
```

Não chegou a ser necessário utilizar recursos para aumentar a capacidade da heap, ou até mesmo força a liberação da mesma, assim como também não foi necessário limpar cache do banco de dados. O app se mostrou eficiente com relação ao consumo de memória em si.