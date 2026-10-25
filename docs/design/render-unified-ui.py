"""Render the static UI proposal. Requires Pillow and Microsoft YaHei on Windows."""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
W, H = 1760, 1310
im = Image.new('RGB', (W, H), '#E8EDF0')
d = ImageDraw.Draw(im)
REG = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'

def text(x, y, s, size=18, color='#41484D', bold=False):
    d.text((x, y), s, font=ImageFont.truetype(BOLD if bold else REG, size), fill=color)

def box(x, y, w, h, color, r=24, outline=None):
    d.rounded_rectangle((x, y, x+w, y+h), r, fill=color, outline=outline, width=1)

def icon(x,y,kind,color='#315A73'):
    if kind=='photo':
        d.rounded_rectangle((x+1,y+9,x+29,y+29),5,outline=color,width=2)
        d.rectangle((x+9,y+4,x+21,y+9),fill=color)
        d.ellipse((x+10,y+14,x+20,y+24),outline=color,width=2)
    elif kind=='audio':
        d.rounded_rectangle((x+10,y+2,x+20,y+21),5,outline=color,width=2)
        d.arc((x+5,y+9,x+25,y+28),0,180,fill=color,width=2)
        d.line((x+15,y+27,x+15,y+33),fill=color,width=2)
        d.line((x+9,y+33,x+21,y+33),fill=color,width=2)
    else:
        d.rounded_rectangle((x,y+7,x+21,y+28),4,outline=color,width=2)
        d.polygon([(x+21,y+13),(x+31,y+8),(x+31,y+27),(x+21,y+22)],fill=color)

def phone(x, label, title, dark=False, running=False):
    y=180; bg='#101416' if dark else '#F7F9FB'; fg='#E1E3E6' if dark else '#181C1F'
    text(x,138,label,18,bold=True)
    box(x+3,y+8,390,762,'#D0D9DE',34)
    box(x,y,390,762,bg,34)
    text(x+24,y+20,'9:41',14,fg,bold=True)
    text(x+310,y+20,'•••',14,fg)
    d.rounded_rectangle((x+346,y+26,x+363,y+35),2,outline=fg,width=1)
    d.rectangle((x+348,y+28,x+359,y+33),fill=fg)
    d.line((x+365,y+29,x+365,y+32),fill=fg,width=2)
    is_home = title == 'Alibi'
    if not is_home:
        d.line((x+42,y+85,x+24,y+85),fill=fg,width=2)
        d.line((x+24,y+85,x+32,y+77),fill=fg,width=2)
        d.line((x+24,y+85,x+32,y+93),fill=fg,width=2)
    text(x+(24 if is_home else 57),y+67,title,28,fg,True)
    if running:
        d.ellipse((x+112,y+82,x+122,y+92),fill='#276A49')
    box(x+330,y+64,40,40,'#E3EBEF' if not dark else '#283238',20)
    if is_home:
        cx,cy=x+350,y+84
        d.ellipse((cx-8,cy-8,cx+8,cy+8),outline=fg,width=2)
        d.ellipse((cx-3,cy-3,cx+3,cy+3),outline=fg,width=2)
        for a in range(0,360,45):
            rad=math.radians(a)
            d.line((cx+8*math.cos(rad),cy+8*math.sin(rad),cx+12*math.cos(rad),cy+12*math.sin(rad)),fill=fg,width=3)
    else:
        text(x+339,y+68,'···',22,fg,True)
    box(x+147,y+740,96,4,'#869297',2)
    return y

text(50,28,'Alibi / 统一 UI 设计',38,'#183A4D',True)
text(52,85,'Material 3 Expressive 方向 · 2026.10.25 · 静态提案，非 App 实机截图',20)

x=50;y=phone(x,'01 / 首页 · 竖向入口','Alibi')
text(x+24,y+127,'随手记录，专注当下',23,'#181C1F',True)
text(x+24,y+166,'录音、录像与拍照，保持一致的操作体验',15)
box(x+20,y+211,350,96,'#E6EEF3')
text(x+38,y+228,'循环记录',15)
text(x+38,y+253,'保留最近 30 分钟',22,'#183A4D',True)
text(x+310,y+246,'›',30)
for i,(title,sub,kind) in enumerate([('录音','记录声音，后台继续','audio'),('录像','记录画面与声音','video'),('拍照','黑屏拍照 · 悬浮快门','photo')]):
    yy=y+333+i*112
    box(x+20,yy,350,100,'#D1E7F4' if i==2 else '#EAF0F3')
    box(x+36,yy+23,54,54,'#F7F9FB',16)
    icon(x+47,yy+33,kind)
    text(x+106,yy+22,title,21,'#183A4D',True)
    text(x+106,yy+56,sub,14)
    text(x+333,yy+29,'›',27)
text(x+24,y+691,'最近记录',16,'#315A73',True)

x=475;y=phone(x,'02 / 拍照准备 · 单一主动作','拍照')
text(x+24,y+132,'选择拍摄方式',23,'#181C1F',True)
text(x+24,y+173,'模式、画面和保存方式都在这里',16)
for i,(title,sub) in enumerate([('黑屏拍照','轻点屏幕拍照，长按返回'),('悬浮拍照','返回桌面，点击悬浮快门')]):
    yy=y+222+i*119
    box(x+20,yy,350,102,'#D1E7F4' if i==0 else '#EAF0F3',24, '#315A73' if i==0 else None)
    d.ellipse((x+39,yy+40,x+59,yy+60),outline='#315A73',width=2)
    if i==0:d.ellipse((x+44,yy+45,x+54,yy+55),fill='#315A73')
    text(x+78,yy+22,title,21,'#183A4D',True)
    text(x+78,yy+59,sub,15)
box(x+20,y+475,350,120,'#EAF0F3')
text(x+38,y+493,'后置镜头  ·  快速抓拍',18,'#183A4D',True)
text(x+38,y+531,'保存到：系统相册',15)
text(x+38,y+561,'拍照设置  ›',15,'#315A73',True)
box(x+20,y+634,350,58,'#315A73',29)
text(x+141,y+648,'进入黑屏',20,'#FFFFFF',True)
text(x+143,y+708,'查看照片  ›',15,'#315A73')

x=900;y=phone(x,'03 / 设置 · 暗色与分组','拍照设置',True)
text(x+24,y+133,'拍摄',17,'#99CEE0',True)
box(x+20,y+172,350,232,'#202A30')
text(x+38,y+190,'抓拍策略',19,'#E1E3E6',True)
text(x+38,y+224,'速度优先，适合随手记录',14,'#C1C7CC')
for i,t in enumerate(['均衡','快速','运动']):
    box(x+36+i*104,y+265,96,46,'#99CEE0' if i==1 else '#35434B',23)
    text(x+62+i*104,y+276,t,17,'#003543' if i==1 else '#E1E3E6',i==1)
d.line((x+38,y+332,x+350,y+332),fill='#41484D')
text(x+38,y+354,'镜头',18,'#E1E3E6')
text(x+242,y+354,'后置  ›',18,'#99CEE0')
text(x+24,y+438,'反馈与操作',17,'#99CEE0',True)
box(x+20,y+480,350,178,'#202A30')
text(x+38,y+501,'保存后短震动',19,'#E1E3E6',True)
text(x+38,y+534,'仅在照片成功保存后反馈',14,'#C1C7CC')
box(x+294,y+505,54,30,'#99CEE0',15)
d.ellipse((x+320,y+510,x+340,y+530),fill='#003543')
d.line((x+38,y+575,x+350,y+575),fill='#41484D')
text(x+38,y+602,'记住上次变焦',18,'#E1E3E6')
text(x+299,y+602,'关  ›',18,'#C1C7CC')
text(x+25,y+693,'页面随系统主题变化，黑屏仍为纯黑',14,'#C1C7CC')

x=1325;y=phone(x,'04 / 运行 · 状态与操作','Alibi',running=True)
box(x+20,y+140,350,82,'#D7F4E3')
text(x+38,y+155,'录音中',21,'#0B3220',True)
text(x+38,y+188,'后台持续运行 · 最近 30 分钟',15,'#0B3220')
heights=[8,18,12,35,20,48,62,35,28,53,77,42,22,57,34,65,40,26,49,17,34,13]
for i,h in enumerate(heights):box(x+37+i*15,y+345-h/2,5,h,'#6C9BB0',2)
text(x+94,y+422,'00:24',57,'#183A4D',True)
text(x+112,y+505,'已记录 24 秒',17)
box(x+20,y+585,162,56,'#D1E7F4',28)
text(x+77,y+599,'暂停',20,'#0C3449',True)
box(x+194,y+585,176,56,'#315A73',28)
text(x+241,y+599,'保存片段',20,'white',True)
text(x+119,y+675,'结束并处理录音  ›',16,'#315A73')

text(52,982,'拍照画布 / 三种可见度，相同的点击快门',24,'#183A4D',True)
text(52,1026,'向上逐渐显示 · 向下逐渐隐藏 · 双指缩放 · 长按返回 · 成功保存后短震动',17)
for i,(caption,factor) in enumerate([('全黑 · 0%',0),('半透明 · 50%',.5),('全显示 · 100%',1)]):
    xx=52+i*285; yy=1078
    box(xx,yy,260,115,'#000000',20)
    if factor:
        col=lambda c: tuple(int(v*factor) for v in c)
        box(xx+3,yy+3,254,109,col((141,183,196)),17)
        d.polygon([(xx+7,yy+105),(xx+79,yy+38),(xx+156,yy+106)],fill=col((64,115,126)))
        d.polygon([(xx+89,yy+106),(xx+191,yy+24),(xx+253,yy+106)],fill=col((42,83,103)))
    text(xx,yy+127,caption,16)
box(930,1078,780,115,'#F7F9FB',24)
box(951,1104,62,62,'#315A73',31);icon(966,1117,'photo','#FFFFFF')
text(1034,1096,'悬浮快门',21,'#183A4D',True)
text(1034,1136,'点击拍照 · 拖动定位 · 长按展开操作',17)
text(52,1264,'设计参数：20 dp 页面边距 / 24 dp 卡片圆角 / 48 dp 最小触达 / 120–220 ms 常用动效',16)
OUT.mkdir(parents=True,exist_ok=True)
im.save(OUT/'unified-ui-2026-10-25.png')
print(OUT/'unified-ui-2026-10-25.png')
