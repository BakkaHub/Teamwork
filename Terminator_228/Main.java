import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * Клуб знакомств молодых книголюбов (упрощённая версия)
 *
 * В проекте 4 простых паттерна:
 *   1. Singleton — ClubDatabase: одна общая база пользователей
 *   2. Factory   — NotifierFactory: создаёт нужный способ уведомления
 *   3. Strategy  — MatchStrategy: разные способы подбора пары
 *   4. Observer  — NewsBoard: рассылка новостей клуба подписчикам
 */

// ======================= ГЛАВНЫЙ КЛАСС ========================

public class Main {
    public static void main(String[] args) {
        ClubDatabase db = ClubDatabase.getInstance();

        // --- Создаём пользователей ---
        User rustem = new User("Rustem", 19, "Талгар", "telegram", "@rustem_reads");
        rustem.addGenre("фэнтези");
        rustem.addGenre("классика");
        rustem.addGenre("поэзия");

        User erasyl = new User("Erasyl", 21, "Кайназар", "email", "erasyl@mail.kz");
        erasyl.addGenre("фэнтези");
        erasyl.addGenre("фантастика");
        erasyl.addGenre("классика");

        User marlen = new User("Marlen", 19, "Чималган", "sms", "+77010000000");
        marlen.addGenre("детектив");
        marlen.addGenre("романтика");

        User bakytzhan = new User("Bakytzhan", 19, "Каскелен", "email", "bakytzhan@mail.kz");
        bakytzhan.addGenre("фантастика");
        bakytzhan.addGenre("классика");



        // --- 1. Singleton: регистрация в общей базе ---
        System.out.println("=== 1. Регистрация (Singleton) ===");
        db.register(rustem);
        db.register(erasyl);
        db.register(marlen);
        db.register(bakytzhan);// не пройдёт: возраст больше 30
        System.out.println("Всего участников: " + db.getUsers().size());

        ClubDatabase sameDb = ClubDatabase.getInstance();
        System.out.println("Это та же самая база? " + (db == sameDb));

        // --- 2. Strategy: подбор пары разными способами ---
        System.out.println();
        System.out.println("=== 2. Подбор пары для Rustem (Strategy) ===");
        Matchmaker matchmaker = new Matchmaker(new GenreMatchStrategy());
        matchmaker.printMatches(rustem, db.getUsers());

        matchmaker.setStrategy(new CityAgeMatchStrategy());
        matchmaker.printMatches(rustem, db.getUsers());

        // --- 3. Factory: уведомления через разные каналы ---
        System.out.println();
        System.out.println("=== 3. Уведомления о знакомстве (Factory) ===");
        User best = matchmaker.findBest(rustem, db.getUsers());
        if (best != null) {
            Notifier n1 = NotifierFactory.create(rustem.getChannel());
            n1.send(rustem, "Познакомьтесь с " + best.getName() + "!");

            Notifier n2 = NotifierFactory.create(best.getChannel());
            n2.send(best, "Познакомьтесь с " + rustem.getName() + "!");
        }

        // --- 4. Observer: новости клуба ---
        System.out.println();
        System.out.println("=== 4. Новости клуба (Observer) ===");
        NewsBoard board = new NewsBoard();
        for (User u : db.getUsers()) {
            board.subscribe(u);
        }
        board.publish("Встреча клуба: обсуждаем «Мастер и Маргарита», суббота 18:00");

        board.unsubscribe(marlen);
        board.publish("Новая книга месяца: «Маленький принц»");
    }
}

// ======================= МОДЕЛЬ ================================

class User {
    private String name;
    private int age;
    private String city;
    private String channel;   // "email", "telegram" или "sms"
    private String contact;
    private ArrayList<String> genres = new ArrayList<>();

    public User(String name, int age, String city, String channel, String contact) {
        this.name = name;
        this.age = age;
        this.city = city;
        this.channel = channel;
        this.contact = contact;
    }

    public void addGenre(String genre) {
        genres.add(genre);
    }

    public String getName()              { return name; }
    public int getAge()                  { return age; }
    public String getCity()              { return city; }
    public String getChannel()           { return channel; }
    public String getContact()           { return contact; }
    public ArrayList<String> getGenres() { return genres; }

    // Observer: пользователь получает новость
    public void receiveNews(String news) {
        Notifier notifier = NotifierFactory.create(channel);
        notifier.send(this, news);
    }

    @Override
    public String toString() {
        return name + ", " + age + ", " + city;
    }
}

// ======================= 1. SINGLETON ==========================

// Единая база участников.
// Данные хранятся в файле club.db.txt, поэтому консольная программа (Main)
// и окно администратора (AdminGUI), запущенные отдельно, видят одну и ту же базу.
class ClubDatabase {
    private static final int MIN_AGE = 14;
    private static final int MAX_AGE = 30;
    private static final List<String> CHANNELS = Arrays.asList("email", "telegram", "sms");
    private static final File DATA_FILE = new File("club.db.txt");

    private final ArrayList<User> users = new ArrayList<>();
    private String lastSeen = "";   // состояние файла, которое мы видели в последний раз

    // Закрытый конструктор: сразу подгружаем сохранённых участников
    private ClubDatabase() {
        reloadIfChanged();
    }

    // Holder: объект создаётся при первом getInstance(), потокобезопасно
    private static class Holder {
        private static final ClubDatabase INSTANCE = new ClubDatabase();
    }

    public static ClubDatabase getInstance() {
        return Holder.INSTANCE;
    }
