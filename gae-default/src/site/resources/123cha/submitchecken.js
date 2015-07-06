function submitchecken() {
	if (document.fm.x.value == "") {
		alert("请输入姓氏。");
		document.fm.x.focus();
		return false;
	}
	if (document.fm.x.value.length > 2) {
		alert("姓氏输入出错,不能多于2字。");
		document.fm.x.focus();
		return false;
	}
	re = "请重新输入！";
	y = document.fm.y.value;
	m = document.fm.m.value;
	d = document.fm.d.value;
	h = document.fm.h.value;
	f = document.fm.s.value;
	if (y == "" || y < 1901 || y > 2050) {
		alert("年应在1901和2050之间。" + re);
		document.fm.y.focus();
		return false;
	}
	if (m > 12 || m < 1) {
		alert("月应在1与12之间。" + re);
		document.fm.m.focus();
		return false;
	}
	if (d > 31 || d < 1) {
		alert("日应在1与31之间。" + re);
		document.fm.d.focus();
		return false;
	}
	if ((m == 4 || m == 6 || m == 9 || m == 11) && d > 30) {
		alert(m + "月只有30天。" + re);
		document.fm.d.focus();
		return false;
	}
	if (y % 4 != 0 && m == 2 && d > 28) {
		alert(y + "年是平年，2月只有28天。" + re);
		document.fm.d.focus();
		return false;
	}
	if (m == 2 && d > 29) {
		alert(y + "年是闰年，2月只有29天。" + re);
		document.fm.d.focus();
		return false;
	}
	if (h > 23 || h < 0) {
		alert("时应在0与23之间。" + re);
		document.fm.h.focus();
		return false;
	}
	if (f > 59 || f < 0) {
		alert("分应在0与59之间。" + re);
		document.fm.s.focus();
		return false;
	}
	return true;
}
