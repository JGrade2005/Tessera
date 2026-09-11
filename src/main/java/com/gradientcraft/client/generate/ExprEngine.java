package com.gradientcraft.client.generate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A compact evaluator for the subset of WorldEdit's //generate expression
 * language used by the preview. Supports:
 *   - variables x, y, z (inputs), user assignments, and the special "data" var
 *   - arithmetic + - * / % and ^ (power, right-assoc), unary - and !
 *   - comparisons < > <= >= == != (return 1.0 / 0.0)
 *   - logical && || , parentheses, function calls
 *   - multiple ';'-separated statements, compound assigns (+= -= *= /=), return
 *   - functions: sin cos tan asin acos atan atan2 sqrt cbrt abs exp ln log
 *     log10 floor ceil round min max pow sign sinh cosh tanh
 *   - constants pi, e, true, false
 *
 * A block is considered "placed" when the final value is > 0 (comparisons
 * yield 1.0 inside the shape). If the expression assigns "data", that value is
 * reported so the preview can colorize (e.g. rainbow wool).
 *
 * Compile once, evaluate many times. Not thread-safe (reuses one environment).
 */
public final class ExprEngine {

    public static final class ParseException extends RuntimeException {
        public ParseException(String m) { super(m); }
    }

    public static final class Result {
        public boolean placed;
        public boolean hasData;
        public double data;
    }

    /** A compiled program: a list of statements plus a reusable environment. */
    public static final class Compiled {
        private final List<Stmt> statements;
        private final Map<String, Double> vars = new HashMap<>();
        private final Result result = new Result();

        Compiled(List<Stmt> statements) { this.statements = statements; }

        public Result eval(double x, double y, double z) {
            vars.clear();
            vars.put("x", x);
            vars.put("y", y);
            vars.put("z", z);
            double last = 0.0;
            for (Stmt s : statements) {
                if (s.kind == Stmt.RETURN) {
                    last = s.expr.eval(vars);
                    break;
                } else if (s.kind == Stmt.ASSIGN) {
                    double rhs = s.expr.eval(vars);
                    double cur = get(vars, s.target);
                    switch (s.op) {
                        case "+=" -> rhs = cur + rhs;
                        case "-=" -> rhs = cur - rhs;
                        case "*=" -> rhs = cur * rhs;
                        case "/=" -> rhs = cur / rhs;
                        default   -> { /* plain '=' */ }
                    }
                    vars.put(s.target, rhs);
                } else {
                    last = s.expr.eval(vars);
                }
            }
            result.placed = last > 0.0;
            Double d = vars.get("data");
            result.hasData = d != null;
            result.data = d == null ? 0.0 : d;
            return result;
        }
    }

    // ---- AST ----

    private interface Node { double eval(Map<String, Double> v); }

    private static double get(Map<String, Double> v, String n) {
        Double d = v.get(n);
        return d == null ? 0.0 : d;
    }

    private static final class Stmt {
        static final int EXPR = 0, ASSIGN = 1, RETURN = 2;
        final int kind;
        final String target;  // for ASSIGN
        final String op;      // for ASSIGN: "=", "+=", ...
        final Node expr;
        Stmt(int kind, String target, String op, Node expr) {
            this.kind = kind; this.target = target; this.op = op; this.expr = expr;
        }
    }

    // ---- Tokenizer ----

    private static final class Tok {
        final int type; final String text; final double num;
        static final int NUM = 0, ID = 1, OP = 2, EOF = 3;
        Tok(int type, String text, double num) { this.type = type; this.text = text; this.num = num; }
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                out.add(new Tok(Tok.NUM, s.substring(i, j), Double.parseDouble(s.substring(i, j))));
                i = j; continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                out.add(new Tok(Tok.ID, s.substring(i, j), 0));
                i = j; continue;
            }
            // multi-char operators
            String two = (i + 1 < n) ? s.substring(i, i + 2) : "";
            if (two.equals("<=") || two.equals(">=") || two.equals("==") || two.equals("!=")
                    || two.equals("&&") || two.equals("||")
                    || two.equals("+=") || two.equals("-=") || two.equals("*=") || two.equals("/=")) {
                out.add(new Tok(Tok.OP, two, 0)); i += 2; continue;
            }
            if ("+-*/%^(),;<>=!".indexOf(c) >= 0) {
                out.add(new Tok(Tok.OP, String.valueOf(c), 0)); i++; continue;
            }
            throw new ParseException("Unexpected character '" + c + "'");
        }
        out.add(new Tok(Tok.EOF, "", 0));
        return out;
    }

    // ---- Parser (recursive descent) ----

    private static final class Parser {
        private final List<Tok> t;
        private int p = 0;
        Parser(List<Tok> t) { this.t = t; }

        private Tok peek() { return t.get(p); }
        private Tok next() { return t.get(p++); }
        private boolean isOp(String s) { Tok k = peek(); return k.type == Tok.OP && k.text.equals(s); }
        private boolean eat(String s) { if (isOp(s)) { p++; return true; } return false; }
        private void expect(String s) { if (!eat(s)) throw new ParseException("Expected '" + s + "'"); }

        List<Stmt> parseProgram() {
            List<Stmt> stmts = new ArrayList<>();
            while (eat(";")) { /* skip leading/empty statements */ }
            while (peek().type != Tok.EOF) {
                stmts.add(parseStatement());
                boolean sawSep = false;
                while (eat(";")) sawSep = true;       // consume one or more separators
                if (!sawSep && peek().type != Tok.EOF) {
                    throw new ParseException("Expected ';' or end");
                }
            }
            return stmts;
        }

        private Stmt parseStatement() {
            // return <expr>
            if (peek().type == Tok.ID && peek().text.equals("return")) {
                next();
                return new Stmt(Stmt.RETURN, null, null, parseExpr());
            }
            // assignment: IDENT (= += -= *= /=) expr
            if (peek().type == Tok.ID && p + 1 < t.size()) {
                Tok nx = t.get(p + 1);
                if (nx.type == Tok.OP && (nx.text.equals("=") || nx.text.equals("+=")
                        || nx.text.equals("-=") || nx.text.equals("*=") || nx.text.equals("/="))) {
                    String name = next().text;
                    String op = next().text;
                    return new Stmt(Stmt.ASSIGN, name, op, parseExpr());
                }
            }
            return new Stmt(Stmt.EXPR, null, null, parseExpr());
        }

        private Node parseExpr() { return parseOr(); }

        private Node parseOr() {
            Node a = parseAnd();
            while (isOp("||")) { next(); Node b = parseAnd(); Node aa = a;
                a = v -> (aa.eval(v) != 0 || b.eval(v) != 0) ? 1.0 : 0.0; }
            return a;
        }
        private Node parseAnd() {
            Node a = parseEquality();
            while (isOp("&&")) { next(); Node b = parseEquality(); Node aa = a;
                a = v -> (aa.eval(v) != 0 && b.eval(v) != 0) ? 1.0 : 0.0; }
            return a;
        }
        private Node parseEquality() {
            Node a = parseCmp();
            while (isOp("==") || isOp("!=")) {
                String o = next().text; Node b = parseCmp(); Node aa = a;
                a = o.equals("==") ? (v -> aa.eval(v) == b.eval(v) ? 1.0 : 0.0)
                        : (v -> aa.eval(v) != b.eval(v) ? 1.0 : 0.0);
            }
            return a;
        }
        private Node parseCmp() {
            Node a = parseAdd();
            while (isOp("<") || isOp(">") || isOp("<=") || isOp(">=")) {
                String o = next().text; Node b = parseAdd(); Node aa = a;
                switch (o) {
                    case "<"  -> a = v -> aa.eval(v) <  b.eval(v) ? 1.0 : 0.0;
                    case ">"  -> a = v -> aa.eval(v) >  b.eval(v) ? 1.0 : 0.0;
                    case "<=" -> a = v -> aa.eval(v) <= b.eval(v) ? 1.0 : 0.0;
                    default   -> a = v -> aa.eval(v) >= b.eval(v) ? 1.0 : 0.0;
                }
            }
            return a;
        }
        private Node parseAdd() {
            Node a = parseMul();
            while (isOp("+") || isOp("-")) {
                String o = next().text; Node b = parseMul(); Node aa = a;
                a = o.equals("+") ? (v -> aa.eval(v) + b.eval(v)) : (v -> aa.eval(v) - b.eval(v));
            }
            return a;
        }
        private Node parseMul() {
            Node a = parseUnary();
            while (isOp("*") || isOp("/") || isOp("%")) {
                String o = next().text; Node b = parseUnary(); Node aa = a;
                switch (o) {
                    case "*" -> a = v -> aa.eval(v) * b.eval(v);
                    case "/" -> a = v -> aa.eval(v) / b.eval(v);
                    default  -> a = v -> aa.eval(v) % b.eval(v);
                }
            }
            return a;
        }
        private Node parseUnary() {
            if (isOp("-")) { next(); Node a = parseUnary(); return v -> -a.eval(v); }
            if (isOp("!")) { next(); Node a = parseUnary(); return v -> a.eval(v) == 0 ? 1.0 : 0.0; }
            return parsePower();
        }
        private Node parsePower() {
            Node base = parsePrimary();
            if (isOp("^")) { next(); Node exp = parseUnary(); Node b = base;
                return v -> Math.pow(b.eval(v), exp.eval(v)); }
            return base;
        }
        private Node parsePrimary() {
            Tok k = peek();
            if (eat("(")) { Node e = parseExpr(); expect(")"); return e; }
            if (k.type == Tok.NUM) { next(); double val = k.num; return v -> val; }
            if (k.type == Tok.ID) {
                next();
                if (isOp("(")) return parseCall(k.text);
                String name = k.text;
                switch (name) {
                    case "pi":    return v -> Math.PI;
                    case "e":     return v -> Math.E;
                    case "true":  return v -> 1.0;
                    case "false": return v -> 0.0;
                    default:      return v -> get(v, name);
                }
            }
            throw new ParseException("Unexpected token '" + k.text + "'");
        }
        private Node parseCall(String name) {
            expect("(");
            List<Node> args = new ArrayList<>();
            if (!isOp(")")) {
                args.add(parseExpr());
                while (eat(",")) args.add(parseExpr());
            }
            expect(")");
            return v -> applyFunc(name, args, v);
        }
    }

    private static double applyFunc(String name, List<Node> a, Map<String, Double> v) {
        double a0 = a.isEmpty() ? 0 : a.get(0).eval(v);
        double a1 = a.size() > 1 ? a.get(1).eval(v) : 0;
        switch (name) {
            case "sin":   return Math.sin(a0);
            case "cos":   return Math.cos(a0);
            case "tan":   return Math.tan(a0);
            case "asin":  return Math.asin(a0);
            case "acos":  return Math.acos(a0);
            case "atan":  return Math.atan(a0);
            case "atan2": return Math.atan2(a0, a1);
            case "sqrt":  return Math.sqrt(a0);
            case "cbrt":  return Math.cbrt(a0);
            case "abs":   return Math.abs(a0);
            case "exp":   return Math.exp(a0);
            case "ln":    return Math.log(a0);
            case "log":   return Math.log(a0);
            case "log10": return Math.log10(a0);
            case "floor": return Math.floor(a0);
            case "ceil":  return Math.ceil(a0);
            case "round": return Math.round(a0);
            case "min":   return Math.min(a0, a1);
            case "max":   return Math.max(a0, a1);
            case "pow":   return Math.pow(a0, a1);
            case "sign":  return Math.signum(a0);
            case "sinh":  return Math.sinh(a0);
            case "cosh":  return Math.cosh(a0);
            case "tanh":  return Math.tanh(a0);
            default: throw new ParseException("Unknown function '" + name + "'");
        }
    }

    public static Compiled compile(String src) {
        if (src == null) src = "";
        List<Stmt> stmts = new Parser(tokenize(src)).parseProgram();
        if (stmts.isEmpty()) throw new ParseException("Empty expression");
        return new Compiled(stmts);
    }

    private ExprEngine() {}
}