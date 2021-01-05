package edu.utexas.cs.utopia.cfpchecker.speclang;

import edu.utexas.cs.utopia.cfpchecker.grammarcomp.GrammarCompProxy;
import grammarcomp.grammar.EBNFGrammar;
import org.apache.commons.lang.Validate;
import soot.SootClass;
import soot.SootMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.utexas.cs.utopia.cfpchecker.speclang.SpecUtils.mergeAllAPICalls;

/**
 * Created by kferles on 6/27/18.
 */
public class Specification implements SpecElement
{
    private static GrammarCompProxy G_COMP = GrammarCompProxy.getInstance();

    private List<SpecRule> rules;

    private EBNFGrammar.BNFGrammar<String> specGrammar;

    private Map<SootMethod, Set<APISpecCall>> apiCalls;

    private Set<SootClass> apiClasses;

    public Specification(List<SpecRule> rules, Set<SootClass> apiClasses)
    {
        Validate.notNull(rules);

        this.rules = rules;

        this.specGrammar = G_COMP.createBNFGrammar(rules.stream().map(Object::toString).collect(Collectors.toList()));
        this.apiClasses = apiClasses;
    }

    public Set<SootClass> getApiClasses()
    {
        return apiClasses;
    }

    public List<SpecRule> getRules()
    {
        return rules;
    }

    public EBNFGrammar.BNFGrammar<String> getSpecGrammar()
    {
        return specGrammar;
    }

    public Set<SootMethod> getAPIsInSpecs()
    {
        return getAPICallInstances().keySet();
    }

    @Override
    public Map<SootMethod, Set<APISpecCall>> getAPICallInstances()
    {
        if (apiCalls == null)
            apiCalls = mergeAllAPICalls(rules.toArray(new SpecElement[rules.size()]));
        return apiCalls;
    }
}
