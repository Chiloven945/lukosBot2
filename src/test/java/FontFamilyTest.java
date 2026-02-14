import java.awt.*;

void main() {
    String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Arrays.stream(names).forEach(IO::println);
}