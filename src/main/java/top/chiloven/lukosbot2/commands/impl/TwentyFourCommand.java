package top.chiloven.lukosbot2.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.chiloven.lukosbot2.commands.IBotCommand;
import top.chiloven.lukosbot2.commands.UsageNode;
import top.chiloven.lukosbot2.config.CommandConfigProp;
import top.chiloven.lukosbot2.core.MessageSenderHub;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.model.message.Address;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal;
import static top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument;

@Service
@ConditionalOnProperty(
        prefix = "lukos.commands.control",
        name = "twentyfour",
        havingValue = "true",
        matchIfMissing = true
)
@Log4j2
public class TwentyFourCommand implements IBotCommand {

    private static final double TARGET = 24.0;
    private static final double EPS = 1e-6;

    private final long timeLimit;

    private final MessageSenderHub senderHub;

    private final ConcurrentMap<Long, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "24-game-timeout");
                t.setDaemon(true);
                return t;
            });

    public TwentyFourCommand(MessageSenderHub senderHub, CommandConfigProp config) {
        this.senderHub = senderHub;
        this.timeLimit = config.getTwentyFour().getTimeLimit();
    }

    private void startGame(CommandSource src) {
        Long uid = src.userId();
        if (uid == null) {
            src.reply("当前平台未提供 userId，无法开始 24 点游戏。请在支持 userId 的平台/会话中使用。");
            return;
        }
        long userId = uid;
        long now = System.currentTimeMillis();

        Session existing = sessions.get(userId);
        if (existing != null && !existing.isExpired(now)) {
            src.reply("""
                    你已经有一局 24 点游戏在进行了：
                    数字：%s
                    
                    如果想放弃这一局，可以发送：/24 giveup
                    或直接发送表达式作为答案。
                    """.formatted(formatNums(existing.nums)));
            return;
        }

        Puzzle puzzle = generatePuzzle();
        long expiresAt = now + timeLimit;

        Session session = new Session(
                src.addr(),
                puzzle.nums(),
                puzzle.solution(),
                expiresAt
        );
        sessions.put(userId, session);

        ScheduledFuture<?> future = scheduler.schedule(
                () -> onTimeout(userId, session),
                timeLimit,
                TimeUnit.MILLISECONDS
        );
        session.setTimeoutFuture(future);

        src.reply("""
                新的一局 24 点开始了（限时 %d 秒）！
                使用下面 4 个数字，通过 + - * / 和括号算出 24：
                数字：%s
                
                提交答案：/24 <表达式>
                放弃并看答案：/24 giveup
                """.formatted(timeLimit / 1000, formatNums(puzzle.nums())));
    }

    private void onTimeout(long userId, Session session) {
        long now = System.currentTimeMillis();
        Session current = sessions.get(userId);
        if (current != session || !session.isExpired(now)) {
            return;
        }

        sessions.remove(userId, session);

        String msg = """
                24 点游戏时间到了（%d 秒）。
                题目数字：%s
                一个可能的答案是：
                %s = 24
                """.formatted(
                timeLimit / 1000,
                formatNums(session.nums),
                session.solution
        );

        senderHub.send(OutboundMessage.text(session.addr, msg));
    }

    @Override
    public String name() {
        return "24";
    }

    private void handleInput(CommandSource src, String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            sendUsage(src);
            return;
        }

        Long uid = src.userId();
        if (uid == null) {
            src.reply("当前平台未提供 userId，无法继续 24 点游戏。请在支持 userId 的平台/会话中使用。");
            return;
        }
        long userId = uid;
        long now = System.currentTimeMillis();
        Session session = sessions.get(userId);

        if (session == null || session.isExpired(now)) {
            src.reply("""
                    你当前没有进行中的 24 点游戏，或游戏已经超时。
                    请先发送 /24 开始一局新的游戏。
                    """);
            return;
        }

        if ("giveup".equalsIgnoreCase(input)) {
            giveUp(src, userId, session);
            return;
        }

        checkAnswer(src, userId, session, input);
    }

    @Override
    public String description() {
        return "玩 24 点游戏";
    }

    private void giveUp(CommandSource src, long userId, Session session) {
        cancelSession(userId, session);

        src.reply("""
                好的，这一局就到这里。
                题目数字：%s
                一个可能的答案是：
                %s = 24
                """.formatted(
                formatNums(session.nums),
                session.solution
        ));
    }

    @Override
    public UsageNode usage() {
        return UsageNode.root(name())
                .description(description())
                .syntax("开始一场新游戏")
                .syntax("提交答案表达式", UsageNode.arg("expression"))
                .syntax("放弃作答并公布答案", UsageNode.lit("giveup"))
                .param("expression", "表达式（可使用 + - * / ()，并使用全部给出的 4 个数字）")
                .example(
                        "24",
                        "24 (2+1)*7+3",
                        "24 giveup"
                )
                .build();
    }

    private void checkAnswer(CommandSource src, long userId, Session session, String expr) {
        expr = stripBackslashes(expr);

        EvalResult eval;
        try {
            eval = evalExpression(expr);
        } catch (IllegalArgumentException e) {
            log.warn("Unable to parse expression: {}", expr, e);
            src.reply("无法解析你的表达式，请只使用数字、+ - * / 和括号。\n错误信息：" + e.getMessage());
            return;
        }

        if (Math.abs(eval.value() - TARGET) > EPS) {
            src.reply("""
                    你的结果不是 24，而是：%s
                    可以继续尝试，或发送 /24 giveup 查看答案。
                    """.formatted(eval.value()));
            return;
        }

        int[] expected = session.nums.clone();
        Arrays.sort(expected);

        int[] used = eval.numbers().stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(used);

        if (!Arrays.equals(expected, used)) {
            src.reply("""
                    虽然结果是 24，但你使用的数字与题目不一致。
                    题目数字：%s
                    你使用的数字：%s
                    
                    请只使用这 4 个数字各一次，再试一次吧。
                    """.formatted(
                    formatNums(session.nums),
                    Arrays.toString(used)
            ));
            return;
        }

        cancelSession(userId, session);
        src.reply("""
                恭喜你，回答正确！🎉
                题目数字：%s
                你的答案：%s = 24
                """.formatted(
                formatNums(session.nums),
                expr
        ));
    }

    @Override
    public void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                literal(name())
                        // /24
                        .executes(ctx -> {
                            startGame(ctx.getSource());
                            return 1;
                        })
                        // /24 <input>
                        .then(argument("input", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String input = StringArgumentType.getString(ctx, "input");
                                    handleInput(ctx.getSource(), input);
                                    return 1;
                                })
                        )
        );
    }



    private void cancelSession(long userId, Session session) {
        sessions.remove(userId, session);
        ScheduledFuture<?> f = session.timeoutFuture;
        if (f != null && !f.isDone()) {
            f.cancel(false);
        }
    }

    private String formatNums(int[] nums) {
        return Arrays.stream(nums)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private Puzzle generatePuzzle() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (true) {
            int[] nums = new int[4];
            for (int i = 0; i < 4; i++) {
                nums[i] = rnd.nextInt(1, 14); // 1..13
            }
            String expr = findExpressionFor24(nums);
            if (expr != null) {
                return new Puzzle(nums, expr);
            }
        }
    }

    private String findExpressionFor24(int[] nums) {
        List<Node> list = new ArrayList<>();
        for (int n : nums) {
            list.add(new Node(n, String.valueOf(n)));
        }
        return dfsFind(list);
    }

    private String dfsFind(List<Node> nums) {
        int n = nums.size();
        if (n == 1) {
            if (Math.abs(nums.getFirst().value - TARGET) < EPS) {
                return nums.getFirst().expr;
            }
            return null;
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;

                List<Node> next = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    if (k != i && k != j) {
                        next.add(nums.get(k));
                    }
                }

                Node a = nums.get(i);
                Node b = nums.get(j);

                // + （无序）
                if (i < j) {
                    next.add(new Node(a.value + b.value, "(" + a.expr + "+" + b.expr + ")"));
                    String r = dfsFind(next);
                    if (r != null) return r;
                    next.removeLast();
                }

                // - （有序）
                next.add(new Node(a.value - b.value, "(" + a.expr + "-" + b.expr + ")"));
                String r = dfsFind(next);
                if (r != null) return r;
                next.removeLast();

                // * （无序）
                if (i < j) {
                    next.add(new Node(a.value * b.value, "(" + a.expr + "*" + b.expr + ")"));
                    r = dfsFind(next);
                    if (r != null) return r;
                    next.removeLast();
                }

                // / （有序）
                if (Math.abs(b.value) > EPS) {
                    next.add(new Node(a.value / b.value, "(" + a.expr + "/" + b.expr + ")"));
                    r = dfsFind(next);
                    if (r != null) return r;
                    next.removeLast();
                }
            }
        }
        return null;
    }

    private String stripBackslashes(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("\\", "");
    }

    private EvalResult evalExpression(String expr) {
        List<String> tokens = tokenize(expr);
        List<Integer> numbers = new ArrayList<>();
        Deque<Double> values = new ArrayDeque<>();
        Deque<Character> ops = new ArrayDeque<>();

        tokens.forEach(t -> {
            char c = t.charAt(0);
            if (Character.isDigit(c)) {
                int v = Integer.parseInt(t);
                numbers.add(v);
                values.push((double) v);
            } else if (c == '(') {
                ops.push(c);
            } else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') {
                    applyOp(values, ops.pop());
                }
                if (ops.isEmpty() || ops.pop() != '(') {
                    throw new IllegalArgumentException("mismatched parentheses");
                }
            } else if (isOp(c)) {
                while (!ops.isEmpty() && ops.peek() != '(' && precedence(ops.peek()) >= precedence(c)) {
                    applyOp(values, ops.pop());
                }
                ops.push(c);
            } else {
                throw new IllegalArgumentException("unexpected token: " + t);
            }
        });

        while (!ops.isEmpty()) {
            char op = ops.pop();
            if (op == '(' || op == ')') {
                throw new IllegalArgumentException("mismatched parentheses");
            }
            applyOp(values, op);
        }

        if (values.size() != 1) {
            throw new IllegalArgumentException("invalid expression");
        }

        return new EvalResult(values.pop(), numbers);
    }

    private List<String> tokenize(String expr) {
        String s = expr.replaceAll("\\s+", "");
        if (s.isEmpty()) throw new IllegalArgumentException("empty expression");

        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                tokens.add(s.substring(i, j));
                i = j;
            } else if (isOp(c) || c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException("illegal character: " + c);
            }
        }
        return tokens;
    }

    private boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private int precedence(char op) {
        if (op == '(') return 0;
        return (op == '+' || op == '-') ? 1 : 2;
    }

    private void applyOp(Deque<Double> values, char op) {
        if (values.size() < 2) throw new IllegalArgumentException("not enough operands for " + op);
        double b = values.pop();
        double a = values.pop();
        switch (op) {
            case '+' -> values.push(a + b);
            case '-' -> values.push(a - b);
            case '*' -> values.push(a * b);
            case '/' -> {
                if (Math.abs(b) < EPS) throw new IllegalArgumentException("division by zero");
                values.push(a / b);
            }
            default -> throw new IllegalArgumentException("unknown operator: " + op);
        }
    }

    private record Node(double value, String expr) {
    }

    private record Puzzle(int[] nums, String solution) {
    }

    private record EvalResult(double value, List<Integer> numbers) {
    }

    private static final class Session {
        final Address addr;
        final int[] nums;
        final String solution;
        final long expiresAtMillis;
        volatile ScheduledFuture<?> timeoutFuture;

        Session(Address addr, int[] nums, String solution, long expiresAtMillis) {
            this.addr = addr;
            this.nums = nums;
            this.solution = solution;
            this.expiresAtMillis = expiresAtMillis;
        }

        void setTimeoutFuture(ScheduledFuture<?> future) {
            this.timeoutFuture = future;
        }

        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

}
