import top.chiloven.lukosbot2.commands.kemono.KemonoAPI;

void main() throws IOException {
    KemonoAPI api = KemonoAPI.getKemonoAPI();
    IO.println(api.getFileFromHash("15be29bad5f6010cc16af84731f60a2812fdda0f861fd623f4539a0c61b97d48").getAsString());
}