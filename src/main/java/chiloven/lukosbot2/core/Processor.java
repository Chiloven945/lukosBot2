package chiloven.lukosbot2.core;

import chiloven.lukosbot2.model.MessageIn;
import chiloven.lukosbot2.model.MessageOut;

import java.util.Collections;
import java.util.List;

/**
 * 处理器约定：
 * - 返回 null 与返回 Collections.emptyList() 等价：表示“当前处理器无输出，交由后续处理”。
 * - 返回非空列表：表示“当前处理器产生了输出”，具体是否继续取决于 Pipeline 的运行模式。
 */
public interface Processor {
    List<MessageOut> handle(MessageIn in);

    /**
     * 工具常量：空输出
     */
    List<MessageOut> NO_OUTPUT = Collections.emptyList();

    /**
     * 工具方法：判空（兼容 null）
     */
    static boolean isEmpty(List<MessageOut> r) {
        return r == null || r.isEmpty();
    }
}
