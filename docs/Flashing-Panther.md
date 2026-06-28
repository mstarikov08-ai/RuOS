# Прошивка RuOS на Pixel 7 (panther)

## Симптом

```
FAILED (remote: 'Invalid command resize-logical-partition:system_a:1035993088')
```

`fastbootd` не смог увеличить логический раздел `system_a` (~988 МБ): RuOS добавляет
~30 системных приложений + правки SystemUI/framework, и `system.img` перерос бюджет
**динамической группы разделов**, заданный в стоковом BoardConfig от Google.

## Исправление (две части)

### 1. Размеры динамических разделов

`device/ruos/panther/board_overrides.mk` увеличивает группу `google_dynamic_partitions`
до «размер super − 4 МиБ» — максимум, который влезает в физический `super`. Значение
**выводится** из реального `BOARD_SUPER_PARTITION_SIZE`, поэтому остаётся корректным
независимо от точного размера раздела на устройстве (мы его не хардкодим).

Нельзя положить второй `BoardConfig.mk` для устройства `panther` (AOSP падает на
дубликате), поэтому `tools/integrate_into_aosp.sh` **дописывает** эти строки в конец
`device/google/pantah/panther/BoardConfig.mk` (последнее присвоение побеждает,
идемпотентно).

### 2. vendor.img

На panther `vendor` — это **логический раздел внутри `super`**. «Нет vendor.img»
почти всегда значит, что его прошивали отдельно или не собрали, а не что в разметке
его нет. Оверрайды форсируют `BOARD_USES_VENDORIMAGE`/`*_VENDORDLKM*` и сборку единого
`super.img`, в котором vendor уже лежит. Прошивка `super.img` закрывает и vendor.

## Сборка

```sh
# из корня RuOS:
python3 vendor/ruos/tools/verify/verify_build.py     # статический gate перед сборкой
./tools/integrate_into_aosp.sh ~/aosp                 # линкует RuOS + дописывает overrides

cd ~/aosp
source build/envsetup.sh
lunch ruos_panther-userdebug
make -j$(nproc)
make superimage      # собрать объединённый super.img (system+vendor+product+system_ext+*_dlkm)
```

## Прошивка

`tools/flash-all.sh` прошивает **объединённый `super.img` в `fastbootd`**, а не
отдельные логические разделы — именно это убирает ошибку `resize-logical-partition`,
и заодно прошивает vendor (он внутри super).

```sh
adb reboot bootloader
OUT=$ANDROID_PRODUCT_OUT ./tools/flash-all.sh
```

Что делает скрипт по порядку:
1. (опц.) `bootloader` и `radio` — если передать образы прошивки **cloudripper-14.0**:
   `BOOTLOADER_IMG=bootloader-panther-cloudripper-14.0-*.img RADIO_IMG=radio-panther-*.img ./tools/flash-all.sh`
2. `vbmeta` с `--disable-verity --disable-verification` (модифицированный /system должен грузиться);
3. цепочку загрузки (`boot`, `init_boot`, `dtbo`, `vendor_boot`…);
4. `fastboot reboot fastboot` → `fastboot flash super super.img` (одним образом);
5. `fastboot -w reboot` — стирание userdata и перезагрузка.

Требования: разблокированный загрузчик, свежие `platform-tools` (fastboot ≥ 34).

## Что нужно подтвердить на устройстве

Размер super выводится из апстрима, но сверьтесь на своём аппарате:

```sh
fastboot getvar super-partition-size
```

Если апстрим вдруг задаёт `super` через **retrofit** (две копии разделов в одном super),
группа должна быть «super/2 − overhead»; panther запускался с динамическими разделами
и virtual A/B (не retrofit), поэтому используется полный размер super. Если ваш базовый
device tree отличается — поправьте `board_overrides.mk` соответственно.

## Первый запуск

Первая загрузка оптимизирует приложения (несколько минут). Ловить падения:

```sh
adb logcat -b crash AndroidRuntime:E *:S
adb logcat -s RuOSSystemUI:E      # сюда пишут защитные guard'ы, если фича не поднялась
```
