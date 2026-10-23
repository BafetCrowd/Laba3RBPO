package рбпо.гришин.лаба3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ПроверкаApiTest {
    private static final String СПЕКТАКЛЬ =
            "{\"название\":\"Ревизор\",\"длительностьМинут\":120,\"возрастноеОграничение\":12}";
    private static final String ЗАЛ = "{\"название\":\"Большой\",\"числоМест\":10}";
    private static final String ПОЛЬЗОВАТЕЛЬ = "{\"имя\":\"Никита\"}";
    private static final String[] ПУТИ = {
            "/api/plays", "/api/theatre-halls", "/api/performances", "/api/tickets", "/api/users"
    };

    // Для тестов создаётся отдельная временная БД. Рабочая база не меняется.
    @DynamicPropertySource
    static void отдельнаяБаза(DynamicPropertyRegistry настройки) throws IOException {
        String путь = Files.createTempDirectory("рбпо-лаба3-тест-").resolve("театр").toString();
        String пароль = UUID.randomUUID().toString();
        String парольАдминистратора = UUID.randomUUID().toString();
        настройки.add("DB_URL", () -> "jdbc:h2:file:" + путь);
        настройки.add("DB_USER", () -> "TEATR_TEST");
        настройки.add("DB_PASSWORD", () -> пароль);
        настройки.add("DB_ADMIN_PASSWORD", () -> парольАдминистратора);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate база;

    @BeforeEach
    void очиститьТестовыеТаблицы() {
        for (String таблица : List.of("билеты", "показы", "спектакли", "залы", "пользователи")) {
            база.update("DELETE FROM " + таблица);
        }
    }

    @Test
    void всеПятьОперацийРаботаютДляКаждогоРесурса() throws Exception {
        long[] данные = исходныеДанные();
        long спектакль = данные[0], зал = данные[1], показ = данные[2], пользователь = данные[3];
        long билет = создать("/api/tickets", билет(показ, 3, пользователь));
        long[] номера = {спектакль, зал, показ, билет, пользователь};

        for (int i = 0; i < ПУТИ.length; i++) {
            mvc.perform(get(ПУТИ[i])).andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(номера[i]));
            mvc.perform(get(ПУТИ[i] + "/" + номера[i])).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(номера[i]));
        }
        отправить(patch("/api/plays/" + спектакль), "{\"название\":\"Гамлет\",\"длительностьМинут\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.название").value("Гамлет"))
                .andExpect(jsonPath("$.длительностьМинут").value(120));
        отправить(patch("/api/theatre-halls/" + зал), "{\"название\":\"Основной\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.числоМест").value(10));
        отправить(patch("/api/performances/" + показ), "{\"базоваяЦена\":700}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.базоваяЦена").value(700));
        отправить(patch("/api/tickets/" + билет), "{\"номерМеста\":4,\"idПользователя\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.номерМеста").value(4))
                .andExpect(jsonPath("$.idПользователя").value(пользователь));
        отправить(patch("/api/users/" + пользователь), "{\"имя\":\"Анна\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.имя").value("Анна"));
        assertEquals("Гамлет", база.queryForObject("SELECT название FROM спектакли WHERE id = ?", String.class, спектакль));
        assertEquals(4, база.queryForObject("SELECT номер_места FROM билеты WHERE id = ?", Integer.class, билет));

        // Сначала удаляем зависимые записи, затем их родителей.
        for (int i : new int[]{3, 2, 0, 1, 4}) {
            String адрес = ПУТИ[i] + "/" + номера[i];
            mvc.perform(delete(адрес)).andExpect(status().isNoContent()).andExpect(content().string(""));
            mvc.perform(get(адрес)).andExpect(status().isNotFound());
            mvc.perform(delete(адрес)).andExpect(status().isNotFound());
            mvc.perform(get(ПУТИ[i])).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Test
    void неверныеДанныеИДанныеСервераНельзяПередатьВЗапросе() throws Exception {
        long[] данные = исходныеДанные();
        long билет = создать("/api/tickets", билет(данные[2], 1, данные[3]));
        long[] номера = {данные[0], данные[1], данные[2], билет, данные[3]};
        String[] тела = {СПЕКТАКЛЬ, ЗАЛ, показ(данные[0], данные[1]), билет(данные[2], 2, данные[3]), ПОЛЬЗОВАТЕЛЬ};
        for (int i = 0; i < ПУТИ.length; i++) {
            отправить(post(ПУТИ[i]), "{}").andExpect(status().isBadRequest());
            ObjectNode тело = (ObjectNode) json.readTree(тела[i]);
            тело.put("id", 123);
            отправить(post(ПУТИ[i]), тело.toString()).andExpect(status().isBadRequest());
            отправить(patch(ПУТИ[i] + "/" + номера[i]), тело.toString()).andExpect(status().isBadRequest());
        }
        отправить(patch("/api/plays/" + данные[0]), "{\"длительностьМинут\":0}").andExpect(status().isBadRequest());
        отправить(patch("/api/theatre-halls/" + данные[1]), "{\"числоМест\":0}").andExpect(status().isBadRequest());
        отправить(patch("/api/performances/" + данные[2]), "{\"базоваяЦена\":-1}").andExpect(status().isBadRequest());
        отправить(patch("/api/users/" + данные[3]), "{\"имя\":\" \"}").andExpect(status().isBadRequest());
        отправить(post("/api/users"), "{").andExpect(status().isBadRequest());
        for (String поле : List.of("цена", "суммаВозврата", "текущееВремя")) {
            ObjectNode тело = (ObjectNode) json.readTree(билет(данные[2], 2, данные[3]));
            тело.put(поле, 1);
            отправить(post("/api/tickets"), тело.toString()).andExpect(status().isBadRequest());
            отправить(patch("/api/tickets/" + билет), тело.toString()).andExpect(status().isBadRequest());
        }
        отправить(post("/api/performances/" + данные[2] + "/group-booking"),
                "{\"idПользователя\":" + данные[3] + ",\"первоеМесто\":2,\"количество\":1,\"текущееВремя\":1}")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tickets/999999/purchase")).andExpect(status().isNotFound());
        mvc.perform(post("/api/performances/999999/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void связиИЗанятыеМестаЗащищены() throws Exception {
        long[] данные = исходныеДанные();
        long показ = данные[2], пользователь = данные[3];
        long первый = создать("/api/tickets", билет(показ, 3, пользователь));
        long второй = создать("/api/tickets", билет(показ, 4, пользователь));
        отправить(post("/api/tickets"), билет(999999, 1, пользователь)).andExpect(status().isNotFound());
        отправить(post("/api/tickets"), билет(показ, 1, 999999)).andExpect(status().isNotFound());
        отправить(post("/api/tickets"), билет(показ, 11, пользователь)).andExpect(status().isBadRequest());
        отправить(post("/api/tickets"), билет(показ, 3, пользователь)).andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + второй), "{\"номерМеста\":3}").andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + второй), "{\"номерМеста\":4}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.номерМеста").value(4));
        отправить(patch("/api/theatre-halls/" + данные[1]), "{\"числоМест\":2}").andExpect(status().isConflict());
        long другойЗал = создать("/api/theatre-halls", "{\"название\":\"Другой\",\"числоМест\":20}");
        отправить(patch("/api/performances/" + показ), "{\"idЗала\":" + другойЗал + "}")
                .andExpect(status().isConflict());
        отправить(patch("/api/performances/" + показ), "{\"начало\":\"2099-12-01T19:00:00\"}")
                .andExpect(status().isConflict());
        отправить(patch("/api/performances/" + показ), "{\"статус\":\"ОТМЕНЁН\"}").andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + первый), "{\"статус\":\"КУПЛЕН\"}").andExpect(status().isConflict());
        ObjectNode купленный = (ObjectNode) json.readTree(билет(показ, 5, пользователь));
        купленный.put("статус", "КУПЛЕН");
        отправить(post("/api/tickets"), купленный.toString()).andExpect(status().isConflict());
        for (String адрес : List.of("/api/plays/" + данные[0], "/api/theatre-halls/" + данные[1],
                "/api/performances/" + показ, "/api/users/" + пользователь)) {
            mvc.perform(delete(адрес)).andExpect(status().isConflict());
        }
        mvc.perform(get("/api/tickets/" + второй)).andExpect(jsonPath("$.номерМеста").value(4));
        mvc.perform(get("/api/theatre-halls/" + данные[1])).andExpect(jsonPath("$.числоМест").value(10));
        mvc.perform(get("/api/performances/" + показ)).andExpect(jsonPath("$.idЗала").value(данные[1]));
        отправить(patch("/api/theatre-halls/" + данные[1]), "{\"числоМест\":4}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.числоМест").value(4));
    }

    @Test
    void групповаяБроньПокупкаИВозвратСохраняютЦенуИОсвобождаютМесто() throws Exception {
        long[] данные = исходныеДанные();
        long показ = данные[2];
        JsonNode билеты = группа(показ, данные[3], 1, 3);
        long первый = билеты.get(0).get("id").asLong();
        long второй = билеты.get(1).get("id").asLong();
        assertEquals(3, билеты.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1, билеты.get(i).get("номерМеста").asInt());
            assertEquals("БРОНЬ", билеты.get(i).get("статус").asText());
        }
        отправить(patch("/api/performances/" + показ), "{\"базоваяЦена\":650}").andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + первый + "/purchase")).andExpect(status().isOk())
                .andExpect(jsonPath("$.статус").value("КУПЛЕН"))
                .andExpect(jsonPath("$.цена").value(650));
        mvc.perform(post("/api/tickets/" + первый + "/purchase")).andExpect(status().isConflict());
        mvc.perform(delete("/api/tickets/" + первый)).andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + первый), "{\"номерМеста\":8}").andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + второй), "{\"номерМеста\":1}").andExpect(status().isConflict());
        отправить(patch("/api/performances/" + показ), "{\"базоваяЦена\":800}").andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + второй + "/return")).andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/" + первый + "/return")).andExpect(status().isOk())
                .andExpect(jsonPath("$.статус").value("ВОЗВРАЩЁН"))
                .andExpect(jsonPath("$.цена").value(650))
                .andExpect(jsonPath("$.суммаВозврата").value(650));
        mvc.perform(post("/api/tickets/" + первый + "/return")).andExpect(status().isConflict());
        отправить(patch("/api/tickets/" + второй), "{\"номерМеста\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.номерМеста").value(1));
        создать("/api/tickets", билет(показ, 2, данные[3]));
        assertEquals(4, база.queryForObject("SELECT COUNT(*) FROM билеты", Integer.class));
    }

    @Test
    void неудачнаяГрупповаяБроньНеЗанимаетЧастьМест() throws Exception {
        long[] данные = исходныеДанные();
        long показ = данные[2], пользователь = данные[3];
        создать("/api/tickets", билет(показ, 3, пользователь));
        отправить(post("/api/performances/" + показ + "/group-booking"), группа(пользователь, 1, 3))
                .andExpect(status().isConflict());
        assertEquals(1, база.queryForObject("SELECT COUNT(*) FROM билеты", Integer.class));
        отправить(post("/api/performances/" + показ + "/group-booking"), группа(пользователь, 9, 3))
                .andExpect(status().isBadRequest());
        assertEquals(1, база.queryForObject("SELECT COUNT(*) FROM билеты", Integer.class));
        // После неудачной операции первые два места всё ещё доступны.
        assertEquals(2, группа(показ, пользователь, 1, 2).size());
        отправить(post("/api/performances/" + показ + "/group-booking"), группа(пользователь, 4, 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void отменаПоказаОтменяетБрониИВозвращаетОплату() throws Exception {
        long[] данные = исходныеДанные();
        long показ = данные[2];
        JsonNode билеты = группа(показ, данные[3], 1, 3);
        long купленный = билеты.get(0).get("id").asLong();
        long бронь = билеты.get(1).get("id").asLong();
        long возвращённый = билеты.get(2).get("id").asLong();
        mvc.perform(post("/api/tickets/" + купленный + "/purchase")).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + возвращённый + "/purchase")).andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + возвращённый + "/return")).andExpect(status().isOk());
        mvc.perform(post("/api/performances/" + показ + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.статус").value("ОТМЕНЁН"));
        mvc.perform(get("/api/tickets/" + купленный)).andExpect(jsonPath("$.статус").value("ОТМЕНЁН"))
                .andExpect(jsonPath("$.суммаВозврата").value(500));
        mvc.perform(get("/api/tickets/" + бронь)).andExpect(jsonPath("$.статус").value("ОТМЕНЁН"))
                .andExpect(jsonPath("$.суммаВозврата").value(0));
        mvc.perform(get("/api/tickets/" + возвращённый)).andExpect(jsonPath("$.статус").value("ВОЗВРАЩЁН"))
                .andExpect(jsonPath("$.суммаВозврата").value(500));
        mvc.perform(post("/api/performances/" + показ + "/cancel")).andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/" + бронь + "/purchase")).andExpect(status().isConflict());
        отправить(post("/api/performances/" + показ + "/group-booking"), группа(данные[3], 4, 2))
                .andExpect(status().isConflict());
        отправить(patch("/api/performances/" + показ), "{\"базоваяЦена\":900}").andExpect(status().isConflict());
        assertEquals(0, база.queryForObject("SELECT COUNT(*) FROM билеты WHERE активное_место IS NOT NULL", Integer.class));
        // Отмена освобождает места, но история билетов должна помещаться в существующий зал.
        отправить(patch("/api/theatre-halls/" + данные[1]), "{\"числоМест\":2}").andExpect(status().isConflict());
    }

    @Test
    void завершениеУчитываетВремяСервераИДлительностьСпектакля() throws Exception {
        long[] данные = исходныеДанные();
        long показ = данные[2];
        JsonNode билеты = группа(показ, данные[3], 1, 2);
        long купленный = билеты.get(0).get("id").asLong();
        long бронь = билеты.get(1).get("id").asLong();
        mvc.perform(post("/api/tickets/" + купленный + "/purchase")).andExpect(status().isOk());
        mvc.perform(post("/api/performances/" + показ + "/complete")).andExpect(status().isConflict());
        // В тестовой БД переносим начало назад: ждать два часа во время теста не нужно.
        база.update("UPDATE показы SET начало = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)), показ);
        mvc.perform(post("/api/performances/" + показ + "/complete")).andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/" + бронь + "/purchase")).andExpect(status().isConflict());
        mvc.perform(post("/api/tickets/" + купленный + "/return")).andExpect(status().isConflict());
        mvc.perform(post("/api/performances/" + показ + "/cancel")).andExpect(status().isConflict());
        отправить(post("/api/tickets"), билет(показ, 3, данные[3])).andExpect(status().isConflict());
        база.update("UPDATE показы SET начало = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusHours(3)), показ);
        mvc.perform(post("/api/performances/" + показ + "/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.статус").value("ЗАВЕРШЁН"));
        mvc.perform(get("/api/tickets/" + купленный)).andExpect(jsonPath("$.статус").value("ИСПОЛЬЗОВАН"))
                .andExpect(jsonPath("$.суммаВозврата").value(0));
        mvc.perform(get("/api/tickets/" + бронь)).andExpect(jsonPath("$.статус").value("ОТМЕНЁН"));
        mvc.perform(post("/api/performances/" + показ + "/complete")).andExpect(status().isConflict());
    }

    @Test
    void одновременныеБрониНеПродаютОдниМестаДважды() throws Exception {
        long[] данные = исходныеДанные();
        var потоки = Executors.newFixedThreadPool(2);
        var старт = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> запрос = () -> {
                старт.await();
                return отправить(post("/api/performances/" + данные[2] + "/group-booking"), группа(данные[3], 1, 2))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> первый = потоки.submit(запрос);
            Future<Integer> второй = потоки.submit(запрос);
            старт.countDown();
            List<Integer> ответы = java.util.stream.Stream.of(первый.get(15, TimeUnit.SECONDS), второй.get(15, TimeUnit.SECONDS))
                    .sorted().toList();
            assertEquals(List.of(201, 409), ответы);
            assertEquals(2, база.queryForObject("SELECT COUNT(*) FROM билеты", Integer.class));
        } finally {
            потоки.shutdownNow();
        }
    }

    @Test
    void базаПроверяетСвязиОграниченияИПраваПользователя() throws Exception {
        long[] данные = исходныеДанные();
        long билет = создать("/api/tickets", билет(данные[2], 1, данные[3]));
        for (String sql : List.of(
                "UPDATE залы SET число_мест = 0",
                "UPDATE пользователи SET имя = NULL",
                "UPDATE показы SET базовая_цена = -1",
                "UPDATE билеты SET пользователь_id = 999999",
                "UPDATE билеты SET сумма_возврата = цена + 1",
                "DELETE FROM спектакли",
                "INSERT INTO билеты (показ_id, номер_места, пользователь_id, статус, цена) SELECT показ_id, номер_места, пользователь_id, статус, цена FROM билеты WHERE id = " + билет)) {
            assertThrows(DataAccessException.class, () -> база.update(sql), sql);
        }
        for (String sql : List.of("CREATE TABLE лишняя_таблица (id INT)", "DROP TABLE билеты",
                "CREATE USER посторонний PASSWORD 'только-для-теста'")) {
            assertThrows(DataAccessException.class, () -> база.execute(sql), sql);
        }
        assertEquals(1, база.queryForObject("SELECT COUNT(*) FROM билеты", Integer.class));
        assertEquals("TEATR_TEST", база.queryForObject("SELECT CURRENT_USER", String.class));
    }

    private long[] исходныеДанные() throws Exception {
        long спектакль = создать("/api/plays", СПЕКТАКЛЬ);
        long зал = создать("/api/theatre-halls", ЗАЛ);
        long пользователь = создать("/api/users", ПОЛЬЗОВАТЕЛЬ);
        long показ = создать("/api/performances", показ(спектакль, зал));
        return new long[]{спектакль, зал, показ, пользователь};
    }

    private ResultActions отправить(MockHttpServletRequestBuilder запрос, String тело) throws Exception {
        return mvc.perform(запрос.contentType(APPLICATION_JSON).content(тело));
    }

    private long создать(String путь, String тело) throws Exception {
        return ответ(отправить(post(путь), тело).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())).get("id").asLong();
    }

    private JsonNode ответ(ResultActions результат) throws Exception {
        return json.readTree(результат.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode группа(long показ, long пользователь, int первоеМесто, int количество) throws Exception {
        return ответ(отправить(post("/api/performances/" + показ + "/group-booking"),
                группа(пользователь, первоеМесто, количество)).andExpect(status().isCreated()));
    }

    private String группа(long пользователь, int первоеМесто, int количество) {
        return "{\"idПользователя\":%d,\"первоеМесто\":%d,\"количество\":%d}"
                .formatted(пользователь, первоеМесто, количество);
    }

    private String показ(long спектакль, long зал) {
        return """
                {"idСпектакля":%d,"idЗала":%d,"начало":"%s","базоваяЦена":500,"статус":"ЗАПЛАНИРОВАН"}
                """.formatted(спектакль, зал, LocalDateTime.now().plusDays(30).withNano(0));
    }

    private String билет(long показ, int место, long пользователь) {
        return """
                {"idПоказа":%d,"номерМеста":%d,"idПользователя":%d,"статус":"БРОНЬ"}
                """.formatted(показ, место, пользователь);
    }
}
