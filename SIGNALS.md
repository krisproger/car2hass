# diplus — полный каталог сигналов (`/api/getVal?name=`)

Источник: `com.van.diplus.cmd.s` (конструктор), декомпиляция diplus 1.3.8-beta18.
Всего **161** сигналов. Ключ `name` — китайская строка (единственное латинское имя — `SOC`).

- **ID** — внутренний индекс в реестре (`SparseArray`), для справки; в HTTP-запросе не используется.
- **Тип**: *num* — числовое значение; *enum* — при `status=true` возвращает текстовую метку, при `status=false` — числовой индекс.
- **Метки enum** — соответствие `индекс=значение` (перевод; `—` = null/зарезервировано).

```
GET http://127.0.0.1:8988/api/getVal?name=车速&status=true   →  {"success":true,"val":"…"}
```

## Ходовые / силовая установка / батарея (1–199)

| ID | Ключ (`name`) | Значение | Тип | Метки enum |
|---:|---|---|---|---|
| 1 | `电源状态` | Power state | enum | 0=off, 1=on, 2=driving |
| 2 | `车速` | Speed (km/h) | num |  |
| 3 | `里程` | Range / mileage | num |  |
| 4 | `档位` | Gear | enum | 0=—, 1=P, 2=R, 3=N, 4=D, 5=M, 6=S |
| 5 | `发动机转速` | Engine RPM | num |  |
| 6 | `刹车深度` | Brake pedal depth | num |  |
| 7 | `加速踏板深度` | Accelerator pedal depth | num |  |
| 8 | `前电机转速` | Front motor RPM | num |  |
| 9 | `后电机转速` | Rear motor RPM | num |  |
| 10 | `发动机功率` | Engine power | num |  |
| 11 | `前电机扭矩` | Front motor torque | num |  |
| 12 | `充电枪插枪状态` | Charge gun plug state | enum | 0=—, 1=disconnected, 2=AC gun, 3=DC gun, 4=adapter gun, 5=discharge gun |
| 13 | `百公里电耗` | Energy per 100 km | num |  |
| 14 | `最高电池温度` | Max battery temp | num |  |
| 15 | `平均电池温度` | Avg battery temp | num |  |
| 16 | `最低电池温度` | Min battery temp | num |  |
| 17 | `最高电池电压` | Max cell voltage | num |  |
| 18 | `最低电池电压` | Min cell voltage | num |  |
| 19 | `上次雨刮时间` | Last wiper time | num |  |
| 20 | `天气` | Weather | enum | 0=clear, 1=rain |
| 21 | `主驾驶安全带状态` | Driver seatbelt state | enum | 0=unbuckled, 1=buckled, 2=invalid |
| 22 | `远程锁车状态` | Remote lock state | enum | 0=unlocked, 1=locked |
| 25 | `车内温度` | Cabin temperature | num |  |
| 26 | `车外温度` | Outside temperature | num |  |
| 27 | `主驾驶空调温度` | Driver A/C set temp | num |  |
| 28 | `温度单位` | Temperature unit | enum | 0=°F, 1=°C |
| 29 | `电池容量` | Battery capacity | num |  |
| 30 | `方向盘转角` | Steering wheel angle | num |  |
| 31 | `方向盘转速` | Steering wheel rate | num |  |
| 32 | `总电耗` | Total energy consumption | num |  |
| 33 | `电量百分比` | Battery charge (%) | num |  |
| 34 | `油量百分比` | Fuel level (%) | num |  |
| 35 | `总燃油消耗` | Total fuel consumption | num |  |
| 36 | `车道线曲率` | Lane curvature | num |  |
| 37 | `右侧线距离` | Right lane distance | num |  |
| 38 | `左侧线距离` | Left lane distance | num |  |
| 39 | `蓄电池电压` | 12V battery voltage | num |  |
| 40 | `雷达左前` | Radar front-left | num |  |
| 41 | `雷达右前` | Radar front-right | num |  |
| 42 | `雷达左后` | Radar rear-left | num |  |
| 43 | `雷达右后` | Radar rear-right | num |  |
| 44 | `雷达左` | Radar left | num |  |
| 45 | `雷达前左中` | Radar front-left-center | num |  |
| 46 | `雷达前右中` | Radar front-right-center | num |  |
| 47 | `雷达中后` | Radar rear-center | num |  |
| 48 | `前雨刮速度` | Front wiper speed | num |  |
| 49 | `雨刮档位` | Wiper mode | num |  |
| 50 | `巡航开关` | Cruise switch | num |  |
| 51 | `前车距离` | Distance to car ahead | num |  |
| 52 | `充电状态` | Charging state | enum | 0=invalid, 1=Ready, 2=started, 3=done, 4=aborted |
| 53 | `左前轮气压` | Tyre pressure FL | num |  |
| 54 | `右前轮气压` | Tyre pressure FR | num |  |
| 55 | `左后轮气压` | Tyre pressure RL | num |  |
| 56 | `右后轮气压` | Tyre pressure RR | num |  |
| 57 | `左转向灯` | Left turn signal | enum | 0=off, 1=on |
| 58 | `右转向灯` | Right turn signal | enum | 0=off, 1=on |
| 59 | `主驾车门锁` | Driver door lock | enum | 0=—, 1=unlocked, 2=locked |
| 61 | `主驾车窗打开百分比` | Window FL open (%) | num |  |
| 62 | `副驾车窗打开百分比` | Window FR open (%) | num |  |
| 63 | `左后车窗打开百分比` | Window RL open (%) | num |  |
| 64 | `右后车窗打开百分比` | Window RR open (%) | num |  |
| 65 | `天窗打开百分比` | Sunroof open (%) | num |  |
| 66 | `遮阳帘打开百分比` | Sunshade open (%) | num |  |
| 67 | `整车工作模式` | Powertrain work mode | enum | 0=stop, 1=EV, 2=forced EV, 3=HEV |
| 68 | `整车运行模式` | Drive mode | enum | 0=NORMAL, 1=ECO, 2=SPORT |
| 69 | `月` | Month | num |  |
| 70 | `日` | Day | num |  |
| 71 | `时` | Hour | num |  |
| 72 | `分` | Minute | num |  |
| 73 | `副驾安全带警告` | Passenger seatbelt warning | enum | 0=—, 1=alarm, 2=normal |
| 74 | `二排左安全带` | 2nd row left seatbelt | enum | 0=unbuckled, 1=buckled, 2=invalid |
| 75 | `二排右安全带` | 2nd row right seatbelt | enum | 0=unbuckled, 1=buckled, 2=invalid |
| 76 | `二排中安全带` | 2nd row center seatbelt | enum | 0=unbuckled, 1=buckled, 2=invalid |
| 77 | `空调状态` | A/C state | enum | 0=off, 1=on |
| 78 | `风量档位` | Fan speed level | num |  |
| 79 | `空调循环方式` | A/C recirculation | enum | 0=fresh, 1=recirc |
| 80 | `空调出风模式` | A/C airflow mode | enum | 0=—, 1=face, 2=face+feet, 3=feet, 4=feet+defrost, 5=defrost, 6=face+feet+defrost, 7=face+defrost |
| 81 | `主驾车门` | Driver door | enum | 0=closed, 1=open |
| 82 | `副驾车门` | Passenger door | enum | 0=closed, 1=open |
| 83 | `左后车门` | Rear-left door | enum | 0=closed, 1=open |
| 84 | `右后车门` | Rear-right door | enum | 0=closed, 1=open |
| 85 | `引擎盖` | Bonnet | enum | 0=closed, 1=open |
| 86 | `后备箱门` | Trunk | enum | 0=closed, 1=open |
| 87 | `油箱盖` | Fuel/charge flap | enum | 0=closed, 1=open |
| 88 | `自动驻车` | Auto hold | enum | 0=disabled, 1=pending, 2=active, 3=state3 |
| 89 | `ACC巡航状态` | ACC cruise state | enum | 0=disabled, 1=cancelled, 2=pending, 3=active, 4=state4, 5=auto-start |
| 90 | `左后接近告警` | Rear-left approach warning | enum | 0=no warning, 1=car approaching, 2=alarm |
| 91 | `右后接近告警` | Rear-right approach warning | enum | 0=no warning, 1=car approaching, 2=alarm |
| 92 | `车道保持状态` | Lane keep state | enum | 0=off, 1=inactive, 2=active1, 3=active2, 4=error |
| 93 | `左后车门锁` | Rear-left door lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 94 | `副驾车门锁` | Passenger door lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 95 | `右后车门锁` | Rear-right door lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 96 | `后备箱门锁` | Trunk lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 97 | `左后儿童锁` | Rear-left child lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 98 | `右后儿童锁` | Rear-right child lock | enum | 0=invalid, 1=unlocked, 2=locked |
| 99 | `小灯` | Sidelights | enum | 0=off, 1=open |
| 100 | `近光灯` | Low beam | enum | 0=off, 1=open |
| 101 | `远光灯` | High beam | enum | 0=off, 1=open |
| 104 | `前雾灯` | Front fog | enum | 0=off, 1=open |
| 105 | `后雾灯` | Rear fog | enum | 0=off, 1=open |
| 106 | `脚照灯` | Footwell light | enum | 0=off, 1=open |
| 107 | `日行灯` | DRL | enum | 0=invalid, 1=open, 2=off, 3=未定义 |
| 108 | `发动机水温` | Engine coolant temp | num |  |
| 109 | `双闪` | Hazard lights | enum | 0=invalid, 1=off, 2=open |
| 110 | `坡度` | Slope / grade | num |  |
| 111 | `雨量` | Rain amount | num |  |
| 112 | `副驾安全带` | Passenger seatbelt | enum | 0=unbuckled, 1=buckled, 2=invalid |
| 113 | `秒` | Second | num |  |
| 114 | `SOC` | Battery SOC (%) | num |  |
| 115 | `转向信号` | Turn signal | enum | 0=off, 1=off, 2=left, 3=left2, 4=right, 5=right2, 6=hazard, 7=emergency, 8=rear flash, 9=flash |

## Мультимедиа / регистратор / система (1000–1199)

| ID | Ключ (`name`) | Значение | Тип | Метки enum |
|---:|---|---|---|---|
| 1001 | `全景状态` | Surround-view state | enum | 0=hidden, 1=shown |
| 1002 | `配置UI版本` | UI config version | enum | 0=UI3, 1=UI4 |
| 1003 | `哨兵状态` | Sentry state | enum |  |
| 1004 | `熄火录像配置开关` | Parked-recording switch | enum |  |
| 1006 | `熄火哨兵报警` | Parked sentry alarm | enum |  |
| 1007 | `WIFI状态` | Wi-Fi state | enum | 0=disconnected, 1=connected |
| 1008 | `蓝牙状态` | Bluetooth state | enum | 0=disconnected, 1=connected |
| 1009 | `蓝牙信号强度` | Bluetooth signal | num |  |
| 1010 | `晃动幅度` | Sway magnitude | num |  |
| 1011 | `振动幅度` | Vibration magnitude | num |  |
| 1016 | `屏幕宽度` | Screen width | num |  |
| 1017 | `屏幕高度` | Screen height | num |  |
| 1018 | `全景记录仪状态` | Dashcam state | enum | 0=stop, 1=starting, 2=running, 3=storage error |
| 1101 | `无线ADB开关` | Wireless ADB switch | enum | 0=off, 1=on |
| 1103 | `媒体音量` | Media volume | num |  |
| 1104 | `导航音量` | Navigation volume | num |  |

## ИИ-распознавание / записи (2000–2099)

| ID | Ключ (`name`) | Значение | Тип | Метки enum |
|---:|---|---|---|---|
| 2001 | `AI识别人可信度` | AI person confidence | num |  |
| 2002 | `AI识别车可信度` | AI vehicle confidence | num |  |
| 2003 | `上次哨兵触发时间` | Last sentry trigger time | num |  |
| 2005 | `上次录像文件开始时间` | Last clip start time | num |  |
| 2006 | `上次录像文件结束时间` | Last clip end time | num |  |
| 2008 | `前车起步状态` | Lead-car start state | enum |  |

## Прочие

| ID | Ключ (`name`) | Значение | Тип | Метки enum |
|---:|---|---|---|---|
| 30001 | `发动机负荷` | Engine load | num |  |
| 30002 | `进气温度` | Intake air temperature | num |  |
| 30003 | `空气质量流量` | MAF air flow | num |  |
| 30004 | `环境温度` | Ambient temperature | num |  |
| 30005 | `机油温度` | Engine oil temperature | num |  |
| 30006 | `燃油消耗率` | Fuel consumption rate | num |  |
| 40001 | `主驾座椅加热` | Driver seat heating | enum |  |
| 40002 | `副驾座椅加热` | Passenger seat heating | enum |  |
| 40003 | `主驾座椅通风` | Driver seat ventilation | enum |  |
| 40004 | `副驾座椅通风` | Passenger seat ventilation | enum |  |
| 40005 | `后排左侧座椅加热` | Rear-left seat heating | enum |  |
| 40006 | `后排右侧座椅加热` | Rear-right seat heating | enum |  |
| 40007 | `方向盘加热` | Steering wheel heating | enum |  |
| 40008 | `后挡风玻璃加热` | Rear window defrost | enum |  |
| 40009 | `充电功率` | Charge rate | num |  |
| 40010 | `后视镜折叠` | Rear mirror fold | enum |  |
