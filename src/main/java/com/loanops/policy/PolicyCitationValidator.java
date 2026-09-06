package com.loanops.policy;

import com.loanops.exception.PolicyCitationValidationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PolicyCitationValidator {

    private static final Pattern CITATION = Pattern.compile("\\[P(\\d+)]");

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
