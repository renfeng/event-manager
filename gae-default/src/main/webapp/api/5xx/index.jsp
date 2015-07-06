<%@ page language="java" trimDirectiveWhitespaces="true"
	contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"
	isErrorPage="true" import="org.json.JSONObject"%>
<%
	String m = pageContext.getException().getLocalizedMessage();
	JSONObject x = new JSONObject();
	x.put("error", m);
	out.print(x);
%>

