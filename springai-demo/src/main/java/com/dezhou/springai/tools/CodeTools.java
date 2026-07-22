package com.dezhou.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeTools {

    @Tool(description = "校验Python代码语法是否合法，返回校验结果（通过或具体错误信息）")
    public String codeCheckPython(@ToolParam(description = "待校验的Python代码字符串") String code) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-c", "import py_compile; import tempfile; import os; " +
                    "f = tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False); " +
                    "f.write(open(0).read()); f.close(); " +
                    "try: py_compile.compile(f.name, doraise=True); print('代码语法无错误') " +
                    "except py_compile.PyCompileError as e: print(str(e)) " +
                    "finally: os.unlink(f.name)");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var os = p.getOutputStream()) {
                os.write(code.getBytes());
                os.flush();
            }
            String out = new String(p.getInputStream().readAllBytes());
            int rc = p.waitFor();
            return rc == 0 ? out.trim() : "语法错误：" + out.trim();
        } catch (Exception e) {
            return "语法校验执行异常：" + e.getMessage();
        }
    }

    @Tool(description = "校验Java代码语法是否合法，尝试编译并返回编译错误信息或通过提示")
    public String codeCheckJava(@ToolParam(description = "待校验的Java代码字符串") String code) {
        String className = extractClassName(code);
        if (className == null) {
            className = "DynamicCodeCheck";
            code = "public class " + className + " { \n" + code + "\n}";
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "环境无Java编译器(JDK)，无法校验";
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);
        File tmpDir;
        try {
            tmpDir = Files.createTempDirectory("javac-check-").toFile();
        } catch (IOException e) {
            return "创建临时目录失败：" + e.getMessage();
        }
        try {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tmpDir));
            JavaFileObject src = new InMemoryJavaSource(className, code);
            Boolean ok = compiler.getTask(null, fm, diagnostics, null, null, List.of(src)).call();
            if (Boolean.TRUE.equals(ok)) {
                return "代码语法无错误";
            }
            StringBuilder sb = new StringBuilder("语法错误：\n");
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                sb.append(String.format("  [行%d 列%d] %s%n", d.getLineNumber(), d.getColumnNumber(), d.getMessage(null)));
            }
            return sb.toString();
        } catch (Exception e) {
            return "编译异常：" + e.getMessage();
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Tool(description = "执行一个简单的数学表达式计算（加减乘除，支持括号），返回计算结果")
    public String calculate(@ToolParam(description = "合法的数学表达式，例如：(3 + 5) * 2 - 7 / 2") String expression) {
        try {
            double result = eval(expression);
            return "计算结果：" + result;
        } catch (Exception e) {
            return "计算失败：" + e.getMessage();
        }
    }

    private static String extractClassName(String code) {
        Pattern p = Pattern.compile("class\\s+(\\w+)");
        Matcher m = p.matcher(code);
        if (m.find()) return m.group(1);
        return null;
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    private static class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;
        InMemoryJavaSource(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        @Override public CharSequence getCharContent(boolean ignore) { return code; }
    }

    private static double eval(String expr) {
        return new Object() {
            int pos = -1, ch;
            void next() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
            boolean eat(int c) { while (ch == ' ') next(); if (ch == c) { next(); return true; } return false; }
            double parse() { next(); double v = parseExpr(); if (pos < expr.length()) throw new RuntimeException("多余字符: "+(char)ch); return v; }
            double parseExpr() { double v = parseTerm(); for (;;) { if (eat('+')) v += parseTerm(); else if (eat('-')) v -= parseTerm(); else return v; } }
            double parseTerm() { double v = parseFactor(); for (;;) { if (eat('*')) v *= parseFactor(); else if (eat('/')) v /= parseFactor(); else return v; } }
            double parseFactor() { if (eat('+')) return +parseFactor(); if (eat('-')) return -parseFactor(); double v; int start = pos; if (eat('(')) { v = parseExpr(); eat(')'); } else if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') next(); v = Double.parseDouble(expr.substring(start, pos)); } else { throw new RuntimeException("非法字符: "+(char)ch); } return v; }
        }.parse();
    }

}
