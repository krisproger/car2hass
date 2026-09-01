# Переход на 3.0: CARTelemetry + Car2Hass

В версии 3.0.0 интеграция переименована в **CARTelemetry** (домен `cartelemetry`),
приложение — в **Car2Hass**. Обновление требует разовых действий.

## Что меняется

| Было | Стало |
|---|---|
| Интеграция `diplus2hass` | `cartelemetry` |
| Сервисы `diplus2hass.send_command` / `.developer_info` | `cartelemetry.send_command` / `.developer_info` |
| Endpoint `/api/byd_diplus` | `/api/cartelemetry` (старый путь временно работает) |
| Приложение DiPlus-to-hass | Car2Hass (ставится как отдельное приложение) |
| Сайт | mytechnic.ru/cartelemetry (старый адрес редиректит) |

## Шаги обновления

1. **Удалите старую запись интеграции**: Настройки → Устройства и службы →
   diplus2hass → Удалить. Запишите имя автомобиля (car_name) до удаления.
2. Перезапустите Home Assistant.
3. Установите интеграцию **cartelemetry-v3.0.5.zip** через HACS
   (Custom repository → `https://github.com/krisproger/diplustohass`) или распакуйте
   вручную в `custom_components/cartelemetry`.
4. Добавьте интеграцию заново с тем же именем автомобиля.
5. Обновите приложение: установите **Car2Hass** (поставится рядом со старым;
   старое можно удалить). Введите те же настройки HA и car_name.
6. **Автоматизации**: найдите и замените `diplus2hass.` → `cartelemetry.`

## Что важно знать

- entity_id сенсоров не содержат имени домена (`sensor.<car_name>_...`) и после
  переустановки создадутся заново с теми же id; история состояний прервётся.
- Устройства в HA создадутся заново (model = Car2Hass).
- Старые APK продолжают отправлять данные через legacy-endpoint `/api/byd_diplus`
  — обновление приложения не обязательно сразу, но рекомендуется.
