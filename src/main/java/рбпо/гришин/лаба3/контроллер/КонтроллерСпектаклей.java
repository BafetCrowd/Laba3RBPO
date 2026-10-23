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
import рбпо.гришин.лаба3.модель.Спектакль;
import рбпо.гришин.лаба3.сервис.СервисТеатра;

import java.util.List;

@RestController
@RequestMapping("/api/plays")
public class КонтроллерСпектаклей {
    private final СервисТеатра сервис;

    public КонтроллерСпектаклей(СервисТеатра сервис) {
        this.сервис = сервис;
    }

    @GetMapping
    public List<Спектакль> получитьВсе() {
        return сервис.получитьСпектакли();
    }

    @GetMapping("/{id}")
    public Спектакль получитьПоНомеру(@PathVariable("id") long id) {
        return сервис.получитьСпектакль(id);
    }

    @PostMapping
    public ResponseEntity<Спектакль> создать(@RequestBody Запрос запрос) {
        Спектакль результат = сервис.создатьСпектакль(преобразовать(запрос));
        return ResponseEntity.status(HttpStatus.CREATED).body(результат);
    }

    @PatchMapping("/{id}")
    public Спектакль изменить(@PathVariable("id") long id, @RequestBody Запрос запрос) {
        return сервис.изменитьСпектакль(id, преобразовать(запрос));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> удалить(@PathVariable("id") long id) {
        сервис.удалитьСпектакль(id);
        return ResponseEntity.noContent().build();
    }

    private Спектакль преобразовать(Запрос запрос) {
        Спектакль данные = new Спектакль();
        данные.название = запрос.название;
        данные.длительностьМинут = запрос.длительностьМинут;
        данные.возрастноеОграничение = запрос.возрастноеОграничение;
        return данные;
    }

    public static class Запрос {
        public String название;
        public Integer длительностьМинут;
        public Integer возрастноеОграничение;
    }
}
