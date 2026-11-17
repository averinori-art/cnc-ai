package ru.cncai.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.util.Locale;
import java.util.regex.*;

public class MainActivity extends Activity {
    LinearLayout page, nav;
    SharedPreferences prefs;
    static final int PICK_GCODE = 4101;
    String loadedProgram = "";
    String loadedName = "";
    String lastReport = "";
    int bg = 0xFF101820, panel = 0xFF1C2935, accent = 0xFF45C4A0, white = 0xFFF2F5F7, muted = 0xFFACBAC5;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        prefs = getSharedPreferences("cncai", MODE_PRIVATE);
        showHome();
    }
    TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, 8, 0, 8); return t;
    }
    LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(20,18,20,18); l.setBackgroundColor(bg); return l;
    }
    void base(String title) {
        page = column();
        TextView brand = text("CNC AI  •  PERSONAL MACHINING ASSISTANT", 11, accent);
        brand.setTypeface(null, Typeface.BOLD); page.addView(brand);
        TextView h = text(title, 27, white); h.setTypeface(null, Typeface.BOLD); page.addView(h);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page);
        setContentView(scroll);
    }
    Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(white); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));
        b.setOnClickListener(v -> action.run()); return b;
    }
    EditText field(String hint, boolean multi) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextColor(white); e.setHintTextColor(muted);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        e.setPadding(12,10,12,10);
        if (multi) { e.setMinLines(5); e.setGravity(Gravity.TOP); e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); }
        else e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    void addNav() {
        page.addView(text(" ",10,white));
        page.addView(button("⌂  Главная", this::showHome));
        page.addView(button("⌘  Анализ G-кода", this::showGcode));
        page.addView(button("▣  Профиль станка", this::showProfile));
        page.addView(button("◉  Режимы резания", this::showCalc));
        page.addView(button("▤  Заметки", this::showNotes));
    }
    void showHome() {
        base("Помощник оператора");
        TextView intro = text("Первая локальная версия. Данные заметок хранятся на телефоне.", 15, muted); page.addView(intro);
        page.addView(button("Анализировать G-код", this::showGcode));
        page.addView(button("Рассчитать обороты и подачу", this::showCalc));
        page.addView(button("Открыть рабочие заметки", this::showNotes));
        TextView warn = text("ВАЖНО: приложение не подключается к станку и не подтверждает безопасность УП. Все программы проверяй по документации конкретного станка и в безопасном режиме.", 13, 0xFFFFC66D);
        warn.setPadding(14,18,14,18); warn.setBackgroundColor(panel); page.addView(warn);
        page.addView(text("В следующих версиях: импорт файлов, база руководств, профили Fanuc и связь с ПК.", 13, muted));
        addNav();
    }
    void showGcode() {
        base("Проверка G-кода");
        page.addView(text("Вставь фрагмент программы. Проверка эвристическая: это не симулятор и не доказательство корректности УП.", 14, muted));
        page.addView(button("Открыть файл .NC / .TXT / .ISO", this::pickGcode));
        TextView fileLabel = text(loadedName.isEmpty() ? "Файл не выбран" : "Загружен: " + loadedName, 12, accent);
        page.addView(fileLabel);
        EditText input = field("Например: G21 G17 G90 ...", true);
        input.setText(loadedProgram);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        page.addView(input);
        TextView result = text("",14,white); result.setPadding(12,12,12,12); result.setBackgroundColor(panel);
        page.addView(button("Проверить программу", () -> result.setText(analyze(input.getText().toString()))));
        page.addView(result); addNav();
    }
    String analyze(String src) {
        if (src.trim().isEmpty()) return "Вставь текст программы или открой файл.";
        String u = src.toUpperCase(Locale.US);
        StringBuilder out = new StringBuilder("CNC AI • ПРЕДВАРИТЕЛЬНЫЙ ОТЧЁТ FANUC\n");
        out.append("Строк в файле: ").append(src.split("\\r?\\n",-1).length).append("\n\n");
        boolean units = Pattern.compile("\\bG\\s*(20|21)\\b").matcher(u).find();
        boolean plane = Pattern.compile("\\bG\\s*(17|18|19)\\b").matcher(u).find();
        boolean mode = Pattern.compile("\\bG\\s*(90|91)\\b").matcher(u).find();
        boolean feedMode = Pattern.compile("\\bG\\s*(93|94|95)\\b").matcher(u).find();
        out.append(check(units, "Единицы G20/G21", "найдены", "не обнаружены; проверь единицы"));
        out.append(check(plane, "Плоскость G17/G18/G19", "найдена", "не обнаружена"));
        out.append(check(mode, "Координаты G90/G91", "найдены", "не обнаружены; проверь режим"));
        out.append(check(feedMode, "Режим подачи G93/G94/G95", "найден", "не обнаружен"));
        out.append("\nМОДАЛЬНЫЕ КОМАНДЫ, ВСТРЕЧЕННЫЕ В ТЕКСТЕ\n");
        String[] groups = {
            "G00/G01/G02/G03", "G17/G18/G19", "G20/G21", "G40/G41/G42",
            "G43/G49", "G54/G55/G56/G57/G58/G59", "G80/G81/G82/G83/G84/G85/G86/G87/G88/G89",
            "G90/G91", "G93/G94/G95", "M03/M04/M05", "M06", "M08/M09", "M30"
        };
        String[] patterns = {
            "G0|G00|G1|G01|G2|G02|G3|G03", "G17|G18|G19", "G20|G21", "G40|G41|G42",
            "G43|G49", "G54|G55|G56|G57|G58|G59", "G80|G81|G82|G83|G84|G85|G86|G87|G88|G89",
            "G90|G91", "G93|G94|G95", "M03|M3|M04|M4|M05|M5", "M06|M6", "M08|M8|M09|M9", "M30"
        };
        for (int i=0;i<groups.length;i++) {
            Matcher m = Pattern.compile("\\b(?:" + patterns[i] + ")\\b").matcher(u);
            if (m.find()) out.append("• ").append(groups[i]).append("\n");
        }
        out.append("\nПРЕДУПРЕЖДЕНИЯ\n");
        boolean warning = false;
        if (!Pattern.compile("\\bM0?5\\b").matcher(u).find()) { out.append("⚠ Не найден M05: проверь останов шпинделя в конце/перед сменой инструмента.\n"); warning=true; }
        if (u.contains("G41") || u.contains("G42")) {
            out.append("⚠ Найдена коррекция радиуса G41/G42: проверь G40, плоскость, подход/отход и таблицу коррекций.\n"); warning=true;
        }
        if (u.contains("G43")) {
            out.append("⚠ Найдена коррекция длины G43: проверь H-корректор и направление оси инструмента.\n"); warning=true;
        }
        if (u.contains("G28") || u.contains("G53")) {
            out.append("⚠ Есть переходы к машинным координатам: проверь формат команды и безопасную последовательность для конкретного станка.\n"); warning=true;
        }
        if (u.contains("G00") || Pattern.compile("\\bG0\\b").matcher(u).find()) {
            out.append("⚠ Есть быстрые перемещения: проверь высоты, заготовку, оснастку и траекторию.\n"); warning=true;
        }
        if (!warning) out.append("Явные триггеры предупреждений не обнаружены. Это не означает, что программа безопасна.\n");
        out.append("\nОГРАНИЧЕНИЯ ПРОВЕРКИ\n");
        out.append("Это текстовый предварительный анализ, не полноценный интерпретатор Fanuc и не симуляция. ");
        out.append("Не проверяются фактическая кинематика, лимиты осей, смещения, макросы, OEM-циклы, инструментальные данные, ");
        out.append("столкновения, состояние PLC и особенности конкретной стойки. Перед запуском требуется проверка по руководству, ");
        out.append("графическая симуляция и безопасный пробный прогон.");
        return out.toString();
    }
    String check(boolean ok, String label, String yes, String no) {
        return (ok ? "✓ " : "⚠ ") + label + ": " + (ok ? yes : no) + "\n";
    }
    void pickGcode() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain","application/octet-stream","application/x-nc","application/iso"});
        startActivityForResult(i, PICK_GCODE);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_GCODE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                StringBuilder sb = new StringBuilder();
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line; int count=0;
                while ((line=br.readLine()) != null && count++ < 20000) sb.append(line).append("\n");
                br.close();
                loadedProgram = sb.toString();
                loadedName = getDisplayName(uri);
                showGcode();
            } catch(Exception ex) {
                Toast.makeText(this, "Не удалось прочитать файл: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
    String getDisplayName(Uri uri) {
        String name = "Выбранный файл";
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try { if (c.moveToFirst()) { int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(idx>=0) name=c.getString(idx); } }
            finally { c.close(); }
        }
        return name;
    }
    void shareReport() {
        if (lastReport.isEmpty()) {
            Toast.makeText(this, "Сначала выполни проверку программы", Toast.LENGTH_SHORT).show(); return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "CNC AI • Отчёт анализа G-кода");
        send.putExtra(Intent.EXTRA_TEXT, (loadedName.isEmpty() ? "" : "Файл: " + loadedName + "\n\n") + lastReport);
        startActivity(Intent.createChooser(send, "Поделиться отчётом"));
    }
    void showProfile() {
        base("Профиль станка");
        page.addView(text("Локальная карточка оборудования. Заполни точные данные по паспорту станка.",14,muted));
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Название / модель станка"); name.setTextColor(white); name.setHintTextColor(muted); name.setText(prefs.getString("machine_name","")); page.addView(name);
        EditText control = new EditText(this); control.setSingleLine(true); control.setHint("Стойка, например Fanuc 31i"); control.setTextColor(white); control.setHintTextColor(muted); control.setText(prefs.getString("control","Fanuc")); page.addView(control);
        EditText axes = new EditText(this); axes.setSingleLine(true); axes.setHint("Оси и кинематика, например XYZ + угловая головка"); axes.setTextColor(white); axes.setHintTextColor(muted); axes.setText(prefs.getString("axes","")); page.addView(axes);
        EditText notes = new EditText(this); notes.setMinLines(3); notes.setGravity(Gravity.TOP); notes.setHint("Особенности, циклы, ограничения (только подтверждённые данные)"); notes.setTextColor(white); notes.setHintTextColor(muted); notes.setText(prefs.getString("machine_notes","")); page.addView(notes);
        page.addView(button("Сохранить профиль", () -> {
            prefs.edit().putString("machine_name",name.getText().toString()).putString("control",control.getText().toString()).putString("axes",axes.getText().toString()).putString("machine_notes",notes.getText().toString()).apply();
            Toast.makeText(this,"Профиль сохранён локально",Toast.LENGTH_SHORT).show();
        }));
        addNav();
    }
    double val(EditText e) { try { return Double.parseDouble(e.getText().toString().replace(',','.')); } catch(Exception ex) { return Double.NaN; } }
    void showCalc() {
        base("Режимы резания");
        page.addView(text("Расчёт оборотов по скорости резания и диаметру.",14,muted));
        EditText vc=field("Скорость Vc, м/мин",false), d=field("Диаметр D, мм",false);
        page.addView(vc); page.addView(d);
        TextView r=text("",16,white); r.setBackgroundColor(panel); r.setPadding(12,12,12,12);
        page.addView(button("Рассчитать обороты", () -> {
            double a=val(vc), b=val(d);
            if (!(a>0 && b>0)) r.setText("Введи положительные числа.");
            else r.setText(String.format(Locale.US,"n = 1000 × Vc / (π × D)\n\nРезультат: %.0f об/мин\n\nЭто расчётная величина. Учитывай ограничения станка, круга/инструмента и технологию.",1000*a/(Math.PI*b)));
        }));
        page.addView(r);
        page.addView(text("Расчёт подачи на оборот.",14,muted));
        EditText f=field("Подача на оборот, мм/об",false), n=field("Обороты, об/мин",false);
        page.addView(f); page.addView(n);
        TextView fr=text("",16,white); fr.setBackgroundColor(panel); fr.setPadding(12,12,12,12);
        page.addView(button("Рассчитать подачу", () -> {
            double a=val(f), b=val(n);
            if (!(a>0 && b>0)) fr.setText("Введи положительные числа.");
            else fr.setText(String.format(Locale.US,"F = f × n\n\nРезультат: %.2f мм/мин",a*b));
        }));
        page.addView(fr); addNav();
    }
    void showNotes() {
        base("Рабочие заметки");
        page.addView(text("Заметки сохраняются локально на этом телефоне.",14,muted));
        EditText e=field("Станок, инструмент, проверенные режимы, наблюдения...",true);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setText(prefs.getString("notes","")); page.addView(e);
        page.addView(button("Сохранить заметки", () -> {
            prefs.edit().putString("notes",e.getText().toString()).apply();
            Toast.makeText(this,"Сохранено на телефоне",Toast.LENGTH_SHORT).show();
        }));
        addNav();
    }
}