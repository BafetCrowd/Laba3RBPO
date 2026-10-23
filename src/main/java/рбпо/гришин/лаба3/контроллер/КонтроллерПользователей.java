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
import рбпо.гришин.лаба3.модель.Пользователь;
import рбпо.гришин.лаба3.сервис.СервисТеатра;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class КонтроллерПользователей {
    private final СервисТеатра сервис;

    public КонтроллерПользователей(СервисТеатра сервис) {
        this.сервис = сервис;
    }

    @GetMapping
    public List<Пользователь> получитьВсе() {
        return сервис.получитьПользователей();
    }

    @GetMapping("/{id}")
    public Пользователь получитьПоНомеру(@PathVariable("id") long id) {
        return сервис.получитьПользователя(id);
    }

    @PostMapping
    public ResponseEntity<Пользователь> создать(@RequestBody Запрос запрос) {
        Пользователь результат = сервис.создатьПользователя(преобразовать(запрос));
        return ResponseEntity.status(HttpStatus.CREATED).body(результат);
    }

    @PatchMapping("/{id}")
    public Пользователь изменить(@PathVariable("id") long id, @RequestBody Запрос запрос) {
        return сервис.изменитьПользователя(id, преобразовать(запрос));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> удалить(@PathVariable("id") long id) {
        сервис.удалитьПользователя(id);
        return ResponseEntity.noContent().build();
    }

    private Пользователь преобразовать(Запрос запрос) {
        Пользователь данные = new Пользователь();
        данные.имя = запрос.имя;
        return данные;
    }

    public static class Запрос {
        public String имя;
    }
}
