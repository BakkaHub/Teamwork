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

    // ---------- Проверка данных ----------

    // Возвращает причину отказа или null, если данные в порядке
    public static String validationError(User u) {
        if (u == null || u.getName() == null || u.getName().trim().isEmpty()) {
            return "пустое имя";
        }
        if (u.getAge() < MIN_AGE || u.getAge() > MAX_AGE) {
            return "клуб для молодёжи от " + MIN_AGE + " до " + MAX_AGE + " лет";
        }
        if (!CHANNELS.contains(u.getChannel())) {
            return "неизвестный канал связи «" + u.getChannel() + "»";
        }
        if (u.getContact() == null || u.getContact().trim().isEmpty()) {
            return "не указан контакт";
        }
        return null;
    }

    public static List<String> getChannels() {
        return CHANNELS;
    }

    // ---------- Регистрация, изменение, удаление ----------

    public synchronized boolean register(User user) {
        String error = validationError(user);
        if (error != null) {
            System.out.println("Отказ: " + (user == null ? "?" : user.getName()) + " — " + error);
            return false;
        }
        reloadIfChanged();
        int i = indexOfContact(user.getContact());
        if (i >= 0) {
            if (users.get(i).getName().equals(user.getName())) {
                // тот же человек уже есть в файле: обновляем запись
                users.set(i, user);
                save();
                System.out.println("Уже в базе (данные обновлены): " + user);
                return true;
            }
            System.out.println("Отказ: " + user.getName() + " — контакт "
                    + user.getContact() + " уже зарегистрирован");
            return false;
        }
        users.add(user);
        save();
        System.out.println("Зарегистрирован: " + user);
        return true;
    }

    // Замена записи обновлённой (для редактирования)
    public synchronized boolean replaceUser(User old, User updated) {
        if (old == null || validationError(updated) != null) {
            return false;
        }
        reloadIfChanged();
        int i = indexOfContact(old.getContact());
        if (i < 0) {
            return false;
        }
        int other = indexOfContact(updated.getContact());
        if (other >= 0 && other != i) {
            return false;   // новый контакт уже занят другим
        }
        users.set(i, updated);
        save();
        return true;
    }

    public synchronized boolean remove(String contact) {
        reloadIfChanged();
        int i = indexOfContact(contact);
        if (i < 0) {
            return false;
        }
        User u = users.remove(i);
        save();
        System.out.println("Удалён из базы: " + u.getName());
        return true;
    }

    public synchronized void clearUsers() {
        reloadIfChanged();
        users.clear();
        save();
    }

    // ---------- Поиск ----------

    public synchronized User findByContact(String contact) {
        reloadIfChanged();
        int i = indexOfContact(contact);
        return i < 0 ? null : users.get(i);
    }

    public synchronized User findByName(String name) {
        reloadIfChanged();
        for (User u : users) {
            if (u.getName().equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    public synchronized ArrayList<User> findByCity(String city) {
        reloadIfChanged();
        ArrayList<User> result = new ArrayList<>();
        for (User u : users) {
            if (u.getCity().equalsIgnoreCase(city)) {
                result.add(u);
            }
        }
        return result;
    }

    public synchronized ArrayList<User> findByGenre(String genre) {
        reloadIfChanged();
        ArrayList<User> result = new ArrayList<>();
        for (User u : users) {
            if (u.getGenres().contains(genre)) {
                result.add(u);
            }
        }
        return result;
    }

    // ---------- Доступ к данным ----------

    // Копия списка: снаружи базу не испортить
    public synchronized ArrayList<User> getUsers() {
        reloadIfChanged();
        return new ArrayList<>(users);
    }

    public synchronized int count() {
        reloadIfChanged();
        return users.size();
    }

    // ---------- Файл базы ----------
    // Строка: имя <TAB> возраст <TAB> город <TAB> канал <TAB> контакт <TAB> жанр1,жанр2

    private int indexOfContact(String contact) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getContact().equals(contact)) {
                return i;
            }
        }
        return -1;
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // Перечитывает файл, если его изменила другая программа.
    // Возвращает true, если данные обновились.
    public synchronized boolean reloadIfChanged() {
        if (!DATA_FILE.exists()) {
            return false;
        }
        String stamp = DATA_FILE.lastModified() + ":" + DATA_FILE.length();
        if (stamp.equals(lastSeen)) {
            return false;
        }
        ArrayList<User> loaded = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(DATA_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 6) {
                    continue;
                }
                User u = new User(p[0], Integer.parseInt(p[1].trim()), p[2], p[3], p[4]);
                for (String g : p[5].split(",")) {
                    if (!g.trim().isEmpty()) {
                        u.addGenre(g.trim());
                    }
                }
                loaded.add(u);
            }
        } catch (IOException | NumberFormatException e) {
            return false;
        }
        users.clear();
        users.addAll(loaded);
        lastSeen = stamp;
        return true;
    }

    private void save() {
        try (PrintWriter out = new PrintWriter(DATA_FILE, "UTF-8")) {
            for (User u : users) {
                out.println(clean(u.getName()) + "\t" + u.getAge() + "\t" + clean(u.getCity())
                        + "\t" + u.getChannel() + "\t" + clean(u.getContact()) + "\t"
                        + clean(String.join(",", u.getGenres())));
            }
        } catch (IOException e) {
            System.out.println("Не удалось сохранить базу: " + e.getMessage());
            return;
        }
        lastSeen = DATA_FILE.lastModified() + ":" + DATA_FILE.length();
    }
}

