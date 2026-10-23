package рбпо.гришин.лаба3.контроллер;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import рбпо.гришин.лаба3.модель.Показ;
import рбпо.гришин.лаба3.сервис.СервисТеатра;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/performances")
public class КонтроллерПоказов {
    private final СервисТеатра сервис;

    public КонтроллерПоказов(СервисТеатра сервис) {
        this.сервис = сервис;
    }

    @GetMapping
    public List<Показ> получитьВсе() {
        return сервис.получитьПоказы();
    }

    @GetMapping("/{id}")
    public Показ получитьПоНомеру(@PathVariable("id") long id) {
        return сервис.получитьПоказ(id);
    }

    @PostMapping
    public ResponseEntity<Показ> создать(@RequestBody Запрос запрос) {
        Показ результат = сервис.создатьПоказ(преобразовать(запрос));
        return ResponseEntity.status(HttpStatus.CREATED).body(результат);
    }

    @PatchMapping("/{id}")
    public Показ изменить(@PathVariable("id") long id, @RequestBody Запрос запрос) {
        return сервис.изменитьПоказ(id, преобразовать(запрос));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> удалить(@PathVariable("id") long id) {
        сервис.удалитьПоказ(id);
        return ResponseEntity.noContent().build();
    }

    private Показ преобразовать(Запрос запрос) {
        Показ данные = new Показ();
        данные.idСпектакля = запрос.idСпектакля;
        данные.idЗала = запрос.idЗала;
        данные.начало = запрос.начало;
        данные.базоваяЦена = запрос.базоваяЦена;
        данные.статус = запрос.статус;
        return данные;
    }

    public static class Запрос {
        public Long idСпектакля;
        public Long idЗала;
        public LocalDateTime начало;
        public BigDecimal базоваяЦена;
        public String статус;
    }
}
