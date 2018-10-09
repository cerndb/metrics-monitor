package ch.cern.exdemon.monitor.trigger.action.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.cern.utils.StringUtils;
import scala.util.matching.Regex;

public class TemplateString {

    private String template;

    public TemplateString(String template) {
        this.template = template;
    }

    @Override
    public String toString() {
        return template;
    }

    public void replace(String key, Object value) {
        key = "<".concat(key).concat(">");
        
        template = template.replaceAll(Regex.quote(key), StringUtils.removeTrailingZerosIfNumber(Matcher.quoteReplacement(String.valueOf(value))));
    }

    public void replaceKeys(String mainKey, Map<String, ?> attributes) {
        replaceKeys(mainKey, new ValueSupplier() {
            @Override
            public Object get(String key) {
                if(attributes == null || !attributes.containsKey(key))
                    return null;
                
                return StringUtils.removeTrailingZerosIfNumber(attributes.get(key).toString());
            }
        });
    }
    
    public void replaceKeys(String mainKey, ValueSupplier valueSupplier) {
        Matcher matcher = Pattern.compile("\\<"+mainKey+":([^>]+)\\>").matcher(template);        
        
        while (matcher.find()) {
            String key = matcher.group(1);
            
            if(valueSupplier != null && valueSupplier.get(key) != null) {
                String value = StringUtils.removeTrailingZerosIfNumber(valueSupplier.get(key).toString());
                
                replace(mainKey + ":" + key, value);
            }else {
                replace(mainKey + ":" + key, "null");
            }
        }
    }

    public void replaceContainer(String key, ValueSupplier valueSupplier) {
        String opnening = "<"+key+">";
        String closing = "</"+key+">";
        
        int startKey = template.indexOf(opnening);
        
        while (startKey > -1){
            int endKey = template.indexOf(closing, startKey);
            
            String preText = template.substring(0, startKey);
            String subTemplate = template.substring(startKey + opnening.length(), endKey);
            String postText = template.substring(endKey + closing.length());
        
            Object newValue = valueSupplier.get(subTemplate);
            
            template = preText + newValue + postText;

            startKey = template.indexOf(opnening, endKey);
        }
        
    }
    
    protected TemplateString clone() {
        return new TemplateString(template);
    }
    
}
