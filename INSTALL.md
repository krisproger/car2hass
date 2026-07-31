# Установка DiPlus-to-hass / Installing DiPlus-to-hass

## Русский

### 1. Установите DiPlus на головное устройство

Приложение **DiPlus** — обязательный источник данных. Без него DiPlus-to-hass работать не будет.

1. Скачайте [diplus.1.3.8-beta18.apk](https://teplitzky.ru/diplus2hass/download.php?file=diplus).
2. Установите на ГУ (флешка, мессенджер, ADB), запустите и выдайте разрешения.
3. Проверьте, что DiPlus показывает данные автомобиля.

### 2. Установите интеграцию Home Assistant

Есть два способа — через HACS (рекомендуется) или вручную.

#### Способ A. Через HACS (рекомендуется)

1. Убедитесь, что в HA установлен [HACS](https://hacs.xyz/).
2. **HACS → Integrations → меню ⋮ (вверху справа) → Custom repositories**.
3. В поле Repository вставьте `https://github.com/krisproger/diplustohass`, категория — **Integration** → **Add**.
4. Найдите в HACS **DiPlus-to-hass — BYD Vehicle Telemetry** и нажмите **Download**.
5. Перезапустите Home Assistant.
6. **Настройки → Устройства и службы → Добавить интеграцию** → **DiPlus-to-hass — BYD Vehicle Telemetry**.
7. Введите имя автомобиля латиницей без пробелов (например, `BYDSongPRO`) — оно станет префиксом сущностей.

Обновления интеграции будут приходить через HACS автоматически.

#### Способ B. Вручную (без HACS)

1. Скачайте [diplus2hass-v2.1.2.zip](https://teplitzky.ru/diplus2hass/download.php?file=integration) и распакуйте.
2. Скопируйте папку `custom_components/diplus2hass` в `/config/custom_components/`.

    ```bash
    cp -r custom_components/diplus2hass /config/custom_components/
    ```

    **При обновлении:** сначала полностью удалите старую папку, затем копируйте новую — перезапись поверх приводит к смешанному набору файлов и ошибкам импорта.

3. Перезапустите Home Assistant.
4. **Настройки → Устройства и службы → Добавить интеграцию** → **DiPlus-to-hass — BYD Vehicle Telemetry**.
5. Введите имя автомобиля латиницей без пробелов (например, `BYDSongPRO`) — оно станет префиксом сущностей.

### 3. Создайте Long-Lived Access Token

Профиль пользователя HA → **Long-Lived Access Tokens** → создайте токен и скопируйте его (показывается один раз).

### 4. Установите приложение

1. Скачайте [diplus2hass-v2.1.2.apk](https://teplitzky.ru/diplus2hass/download.php?file=app).
2. Установите на ГУ, при первом запуске выдайте разрешения на геолокацию (в т.ч. фоновую).

### 5. Привяжите приложение к HA

В **Настройках** приложения:

1. **HA адрес сервера** — `host:port` (например, `192.168.1.10:8123`); без порта подставляется `8123` (или `443` для HTTPS).
2. **HTTPS** — включите при TLS.
3. **Токен** — токен из шага 3 (значок `?` покажет подсказку).
4. **Имя автомобиля** — точно как в интеграции.
5. **Проверить соединение**.

### 6. Включите передачу

1. На главном экране включите **Отправлять в HA**.
2. Отметьте нужные сигналы в таблице (галочка в шапке — все).
3. Через несколько секунд в HA появятся сущности `sensor.<имя_авто>_*`.

### 7. Автозапуск и фон

1. Пункт меню автозапуска открывает системное окно BYD **AppStartManagement** — разрешите автозапуск.
2. Исключите приложение из оптимизации батареи.
3. Работа при выключенном авто — см. раздел «Фоновый режим» в [руководстве](https://teplitzky.ru/diplus2hass/manual/app/background/).

---

## English

### 1. Install DiPlus on the head unit

The **DiPlus** app is the mandatory data source — DiPlus-to-hass will not work without it.

1. Download [diplus.1.3.8-beta18.apk](https://teplitzky.ru/diplus2hass/download.php?file=diplus).
2. Install it (USB, messenger, ADB), launch and grant permissions.
3. Confirm DiPlus shows live vehicle data.

### 2. Install the Home Assistant integration

Two methods — via HACS (recommended) or manually.

#### Method A. Via HACS (recommended)

1. Make sure [HACS](https://hacs.xyz/) is installed in your HA.
2. **HACS → Integrations → ⋮ menu (top right) → Custom repositories**.
3. Paste `https://github.com/krisproger/diplustohass` into Repository, choose category **Integration** → **Add**.
4. Find **DiPlus-to-hass — BYD Vehicle Telemetry** in HACS and click **Download**.
5. Restart Home Assistant.
6. **Settings → Devices & Services → Add integration** → **DiPlus-to-hass — BYD Vehicle Telemetry**.
7. Enter a car name (latin, no spaces — e.g. `BYDSongPRO`); it becomes the entity prefix.

Integration updates will arrive via HACS automatically.

#### Method B. Manually (no HACS)

1. Download [diplus2hass-v2.1.2.zip](https://teplitzky.ru/diplus2hass/download.php?file=integration) and extract it.
2. Copy `custom_components/diplus2hass` into `/config/custom_components/`.

    ```bash
    cp -r custom_components/diplus2hass /config/custom_components/
    ```

    **When upgrading:** fully delete the old folder first — copying over it leaves a mixed set of files and import errors.

3. Restart Home Assistant.
4. **Settings → Devices & Services → Add integration** → **DiPlus-to-hass — BYD Vehicle Telemetry**.
5. Enter a car name (latin, no spaces — e.g. `BYDSongPRO`); it becomes the entity prefix.

### 3. Create a Long-Lived Access Token

HA user profile → **Long-Lived Access Tokens** → create and copy the token (shown once).

### 4. Install the app

1. Download [diplus2hass-v2.1.2.apk](https://teplitzky.ru/diplus2hass/download.php?file=app).
2. Install it and grant location permissions (including background).

### 5. Link the app to Home Assistant

In the app's **Settings**:

1. **HA server address** — `host:port` (e.g. `192.168.1.10:8123`); port defaults to `8123` (`443` with HTTPS).
2. **HTTPS** — enable for TLS.
3. **Token** — the token from step 3 (tap `?` for a hint).
4. **Car name** — exactly as in the integration.
5. **Test connection**.

### 6. Enable transmission

1. On the main screen enable **Send data to HA**.
2. Tick the signals you need in the telemetry table (header checkbox = all).
3. Entities `sensor.<car>_*` appear in HA within seconds.

### 7. Auto-start & background

1. The menu's auto-start item opens BYD **AppStartManagement** — allow auto-start.
2. Exclude the app from battery optimization.
3. For ACC-off operation see "Background mode" in the [manual](https://teplitzky.ru/diplus2hass/manual/app/background/).
