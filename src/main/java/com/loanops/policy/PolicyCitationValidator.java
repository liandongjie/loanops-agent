package com.loanops.policy;

import com.loanops.exception.PolicyCitationValidationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验模型回答中的政策引用编号是否属于本轮检索得到的证据。
 * {@code P1}、{@code P2} 是本轮 Policy RAG 上下文为证据分配的临时编号，不是政策库中的永久主键。
 * 本类会拒绝两类问题：回答使用了本轮证据中不存在的“非法引用”，或者在本轮要求引用时完全没有引用。
 *
 * <p>该校验只建立回答编号与本轮证据之间的结构化边界，不能证明模型的每个政策结论都被引用内容
 * 充分支持，也不能证明结论在语义上完全正确。</p>
 */
@Component
public class PolicyCitationValidator {

    private static final Pattern CITATION = Pattern.compile("\\[P(\\d+)]");

    /**
     * 提取并校验回答中形如 {@code [P1]} 的 Citation（政策引用）。
     *
     * @param answer 模型生成并等待校验的回答
     * @param context 本轮 Policy RAG 的检索决定、状态和允许引用的证据
     * @return 回答实际使用的本轮政策引用编号集合，例如 {@code P1}
     * @throws PolicyCitationValidationException 回答含有本轮不存在的引用，或要求引用却没有任何引用
     */
    public Set<String> validate(String answer, PolicyGroundingContext context) {
        Set<String> allowed = context.evidence().stream()
                .map(PolicyGroundingContext.Evidence::citationRef)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> cited = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) cited.add("P" + matcher.group(1));
        if (!allowed.containsAll(cited)) throw new PolicyCitationValidationException("Answer contains unknown policy citation");
        if (context.requiresCitation() && cited.isEmpty()) {
            throw new PolicyCitationValidationException("Grounded policy answer requires a citation");
        }
        return Set.copyOf(cited);
    }
}
