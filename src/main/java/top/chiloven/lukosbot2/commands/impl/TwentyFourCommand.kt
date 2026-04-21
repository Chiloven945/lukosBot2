package top.chiloven.lukosbot2.commands.impl

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.config.CommandConfigProp
import top.chiloven.lukosbot2.core.MessageSenderHub
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.Address
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument
import java.util.*
import java.util.concurrent.*
import kotlin.math.abs

@Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["twentyfour"],
    havingValue = "true",
    matchIfMissing = true,
)
class TwentyFourCommand(
    private val senderHub: MessageSenderHub,
    config: CommandConfigProp,
) : IBotCommand {

    companion object {

        private val log = LogManager.getLogger(TwentyFourCommand::class.java)

        private const val TARGET = 24.0
        private const val EPS = 1e-6

    }

    private val timeLimit = config.twentyFour.timeLimit

    private val sessions: ConcurrentMap<Long, Session> = ConcurrentHashMap()

    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "24-game-timeout").apply { isDaemon = true }
        }

    override fun name(): String = "24"

    override fun description(): String = "玩 24 点游戏"

    override fun usage(): UsageNode =
        UsageNode.root(name())
            .description(description())
            .syntax("开始一场新游戏")
            .syntax("提交答案表达式", UsageNode.arg("expression"))
            .syntax("放弃作答并公布答案", UsageNode.lit("giveup"))
            .param("expression", "表达式（可使用 + - * / ()，并使用全部给出的 4 个数字）")
            .example(
                "24",
                "24 (2+1)*7+3",
                "24 giveup",
            )
            .build()

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    startGame(ctx.source)
                    1
                }
                .then(
                    argument("input", StringArgumentType.greedyString())
                        .executes { ctx ->
                            handleInput(ctx.source, StringArgumentType.getString(ctx, "input"))
                            1
                        },
                ),
        )
    }

    private fun startGame(src: CommandSource) {
        val userId = src.userId()
        val now = System.currentTimeMillis()
        val existing = sessions[userId]

        if (existing != null && !existing.isExpired(now)) {
            src.reply(
                """
                你已经有一局 24 点游戏在进行了：
                数字：${existing.nums.formatNums()}
                
                如果想放弃这一局，可以发送：/24 giveup
                或直接发送表达式作为答案。
                """.trimIndent(),
            )
            return
        }

        val puzzle = generatePuzzle()
        val expiresAt = now + timeLimit
        val session = Session(
            addr = src.addr(),
            nums = puzzle.nums,
            solution = puzzle.solution,
            expiresAtMillis = expiresAt,
        )

        sessions[userId] = session

        session.timeoutFuture = scheduler.schedule(
            { onTimeout(userId, session) },
            timeLimit,
            TimeUnit.MILLISECONDS,
        )

        src.reply(
            """
            新的一局 24 点开始了（限时 ${timeLimit / 1000} 秒）！
            使用下面 4 个数字，通过 + - * / 和括号算出 24：
            数字：${puzzle.nums.formatNums()}
            
            提交答案：/24 <表达式>
            放弃并看答案：/24 giveup
            """.trimIndent(),
        )
    }

    private fun onTimeout(userId: Long, session: Session) {
        val now = System.currentTimeMillis()
        val current = sessions[userId]

        if (current !== session || !session.isExpired(now)) return

        sessions.remove(userId, session)

        val msg =
            """
            24 点游戏时间到了（${timeLimit / 1000} 秒）。
            题目数字：${session.nums.formatNums()}
            一个可能的答案是：
            ${session.solution} = 24
            """.trimIndent()

        senderHub.send(OutboundMessage.text(session.addr, msg))
    }

    private fun handleInput(src: CommandSource, rawInput: String?) {
        val input = rawInput?.trim().orEmpty()
        if (input.isEmpty()) {
            sendUsage(src)
            return
        }

        val userId = src.userId()
        val now = System.currentTimeMillis()
        val session = sessions[userId]

        if (session == null || session.isExpired(now)) {
            src.reply(
                """
                你当前没有进行中的 24 点游戏，或游戏已经超时。
                请先发送 /24 开始一局新的游戏。
                """.trimIndent(),
            )
            return
        }

        when {
            input.equals("giveup", ignoreCase = true) -> giveUp(src, userId, session)
            else -> checkAnswer(src, userId, session, input)
        }
    }

    private fun giveUp(src: CommandSource, userId: Long, session: Session) {
        cancelSession(userId, session)
        src.reply(
            """
            好的，这一局就到这里。
            题目数字：${session.nums.formatNums()}
            一个可能的答案是：
            ${session.solution} = 24
            """.trimIndent(),
        )
    }

    private fun checkAnswer(src: CommandSource, userId: Long, session: Session, expr: String) {
        val normalizedExpr = expr.stripBackslashes()

        val eval = try {
            evalExpression(normalizedExpr)
        } catch (e: IllegalArgumentException) {
            log.warn("Unable to parse expression: {}", normalizedExpr, e)
            src.reply("无法解析你的表达式，请只使用数字、+ - * / 和括号。\n错误信息：${e.message}")
            return
        }

        if (abs(eval.value - TARGET) > EPS) {
            src.reply(
                """
                你的结果不是 24，而是：${eval.value}
                可以继续尝试，或发送 /24 giveup 查看答案。
                """.trimIndent(),
            )
            return
        }

        val expected = session.nums.copyOf().also(Arrays::sort)
        val used = eval.numbers.toIntArray().also(Arrays::sort)

        if (!expected.contentEquals(used)) {
            src.reply(
                """
                虽然结果是 24，但你使用的数字与题目不一致。
                题目数字：${session.nums.formatNums()}
                你使用的数字：${used.contentToString()}
                
                请只使用这 4 个数字各一次，再试一次吧。
                """.trimIndent(),
            )
            return
        }

        cancelSession(userId, session)
        src.reply(
            """
            恭喜你，回答正确！🎉
            题目数字：${session.nums.formatNums()}
            你的答案：$normalizedExpr = 24
            """.trimIndent(),
        )
    }

    private fun cancelSession(userId: Long, session: Session) {
        sessions.remove(userId, session)
        session.timeoutFuture?.takeIf { !it.isDone }?.cancel(false)
    }

    private fun generatePuzzle(): Puzzle {
        val rnd = ThreadLocalRandom.current()

        while (true) {
            val nums = IntArray(4) { rnd.nextInt(1, 14) }
            val expr = findExpressionFor24(nums)
            if (expr != null) return Puzzle(nums, expr)
        }
    }

    private fun findExpressionFor24(nums: IntArray): String? =
        dfsFind(nums.map { Node(it.toDouble(), it.toString()) })

    private fun dfsFind(nums: List<Node>): String? {
        if (nums.size == 1) {
            val only = nums.first()
            return only.expr.takeIf { abs(only.value - TARGET) < EPS }
        }

        for (i in nums.indices) {
            for (j in nums.indices) {
                if (i == j) continue

                val nextBase = buildList {
                    for (k in nums.indices) {
                        if (k != i && k != j) add(nums[k])
                    }
                }

                val a = nums[i]
                val b = nums[j]

                if (i < j) {
                    dfsFind(nextBase + Node(a.value + b.value, "(${a.expr}+${b.expr})"))?.let { return it }
                }

                dfsFind(nextBase + Node(a.value - b.value, "(${a.expr}-${b.expr})"))?.let { return it }

                if (i < j) {
                    dfsFind(nextBase + Node(a.value * b.value, "(${a.expr}*${b.expr})"))?.let { return it }
                }

                if (abs(b.value) > EPS) {
                    dfsFind(nextBase + Node(a.value / b.value, "(${a.expr}/${b.expr})"))?.let { return it }
                }
            }
        }

        return null
    }

    private fun evalExpression(expr: String): EvalResult {
        val tokens = tokenize(expr)
        val numbers = mutableListOf<Int>()
        val values = ArrayDeque<Double>()
        val ops = ArrayDeque<Char>()

        for (token in tokens) {
            val c = token.first()
            when {
                c.isDigit() -> {
                    val v = token.toInt()
                    numbers += v
                    values.push(v.toDouble())
                }

                c == '(' -> ops.push(c)

                c == ')' -> {
                    while (ops.isNotEmpty() && ops.peek() != '(') {
                        applyOp(values, ops.pop())
                    }
                    if (ops.isEmpty() || ops.pop() != '(') {
                        throw IllegalArgumentException("mismatched parentheses")
                    }
                }

                c.isOperator() -> {
                    while (ops.isNotEmpty() && ops.peek() != '(' && ops.peek().precedence() >= c.precedence()) {
                        applyOp(values, ops.pop())
                    }
                    ops.push(c)
                }

                else -> throw IllegalArgumentException("unexpected token: $token")
            }
        }

        while (ops.isNotEmpty()) {
            val op = ops.pop()
            if (op == '(' || op == ')') {
                throw IllegalArgumentException("mismatched parentheses")
            }
            applyOp(values, op)
        }

        if (values.size != 1) {
            throw IllegalArgumentException("invalid expression")
        }

        return EvalResult(values.pop(), numbers)
    }

    private fun tokenize(expr: String): List<String> {
        val s = expr.replace(Regex("\\s+"), "")
        require(s.isNotEmpty()) { "empty expression" }

        val tokens = mutableListOf<String>()
        var i = 0

        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() -> {
                    var j = i + 1
                    while (j < s.length && s[j].isDigit()) j++
                    tokens += s.substring(i, j)
                    i = j
                }

                c.isOperator() || c == '(' || c == ')' -> {
                    tokens += c.toString()
                    i++
                }

                else -> throw IllegalArgumentException("illegal character: $c")
            }
        }

        return tokens
    }

    private fun applyOp(values: ArrayDeque<Double>, op: Char) {
        if (values.size < 2) {
            throw IllegalArgumentException("not enough operands for $op")
        }

        val b = values.pop()
        val a = values.pop()

        when (op) {
            '+' -> values.push(a + b)
            '-' -> values.push(a - b)
            '*' -> values.push(a * b)
            '/' -> {
                if (abs(b) < EPS) throw IllegalArgumentException("division by zero")
                values.push(a / b)
            }

            else -> throw IllegalArgumentException("unknown operator: $op")
        }
    }

    private fun Char.isOperator(): Boolean = this == '+' || this == '-' || this == '*' || this == '/'

    private fun Char.precedence(): Int = when (this) {
        '(' -> 0
        '+', '-' -> 1
        '*', '/' -> 2
        else -> throw IllegalArgumentException("unknown operator: $this")
    }

    private fun String.stripBackslashes(): String = replace("\\", "")

    private fun IntArray.formatNums(): String = joinToString(", ")

    private data class Node(
        val value: Double,
        val expr: String,
    )

    private data class Puzzle(
        val nums: IntArray,
        val solution: String,
    )

    private data class EvalResult(
        val value: Double,
        val numbers: List<Int>,
    )

    private data class Session(
        val addr: Address,
        val nums: IntArray,
        val solution: String,
        val expiresAtMillis: Long,
        @Volatile var timeoutFuture: ScheduledFuture<*>? = null,
    ) {

        fun isExpired(now: Long): Boolean = now >= expiresAtMillis

    }

}
