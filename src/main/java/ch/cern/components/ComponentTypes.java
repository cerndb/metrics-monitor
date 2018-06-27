package ch.cern.components;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.reflections.Reflections;

import ch.cern.components.Component.Type;
import ch.cern.properties.ConfigurationException;
import ch.cern.properties.Properties;

public class ComponentTypes {
    
    private final static Logger LOG = Logger.getLogger(ComponentTypes.class.getName());
    
    private static Map<Component.Type, Map<String, Class<? extends Component>>> types = new HashMap<>();
    static{
        new Reflections("ch.cern")
	        		.getTypesAnnotatedWith(RegisterComponentType.class)
	        		.stream()
	        		.forEach(ComponentTypes::registerType);
    }
    
	@SuppressWarnings("unchecked")
	private static void registerType(Class<?> componentToRegister) {
	    Class<? extends Component> componentClass;
	    try {
	        componentClass = (Class<? extends Component>) componentToRegister;
	    }catch(ClassCastException e){
	        LOG.error("Component " + componentToRegister + " could not be registered, it must extend " + Component.class);
    		return;
    	}
    		
    	ComponentType typeAnnotation = componentClass.getSuperclass().getAnnotation(ComponentType.class);
    	if(typeAnnotation == null)
    	    typeAnnotation = componentClass.getSuperclass().getSuperclass().getAnnotation(ComponentType.class);
    	if(typeAnnotation == null)
            typeAnnotation = componentClass.getAnnotation(ComponentType.class);
    	if(typeAnnotation == null) {
    	    LOG.error("Component " + componentToRegister + " could not be registered, it does not extend a class with @ComponentType annotation");
    	    return;
    	}
    	
    	Type type = typeAnnotation.value();
    	RegisterComponentType registerAnnotation = componentClass.getAnnotation(RegisterComponentType.class);
    	
        String name = registerAnnotation.value();
            
        if(!types.containsKey(type))
                types.put(type, new HashMap<String, Class<? extends Component>>());
            
        types.get(type).put(name, componentClass);
    }
	
	public static<C extends Component> C build(Component.Type componentType, String id, Properties properties) throws ConfigurationException  {
	    C component = build(componentType, properties);
	    
	    component.setId(id);
	    
		return component;
	}
	
	public static<C extends Component> C build(Type componentType, Properties properties) throws ConfigurationException {
	    String type = componentType.type();
	    if(type == null)
            type = properties.getProperty("type");
        
        if(type == null)
            throw new ConfigurationException(componentType + ": component type cannot be null.");
        
        C component = buildFromAvailableTypes(componentType, type);
        
        if(component == null){
            try {
                component = getComponentInstance(type);
            } catch (Exception e) {
                String message = "Component class could not be loaded, type or class (" + type + ") does not exist. ";
                
                if(getAvailableTypes(componentType) != null)
                	message += "It must be a FQCN or one of: " + getAvailableTypes(componentType).keySet();
                else
                	message += "It must be a FQCN (not built-in components availables)";
                
                LOG.error(message, e);
                throw new ConfigurationException(componentType + ": " + message);
            }
        }
        
        component.config(properties);
        
        return component;
    }

    private static<C extends Component> C buildFromAvailableTypes(Type componentType, String type) throws ConfigurationException {
        Map<String, Class<? extends Component>> availableComponents = getAvailableTypes(componentType);
        
        if(availableComponents == null)
            return null;
        
        Class<? extends Component> component = availableComponents.get(type);
        
        if(component == null)
            return null;
        
        return getComponentInstance(component.getName());
    }

	private static<C extends Component> C getComponentInstance(String clazzName) throws ConfigurationException {
		try {
			@SuppressWarnings("unchecked")
			Class<C> clazz = (Class<C>) Class.forName(clazzName).asSubclass(Component.class);
			
			return clazz.newInstance();
		} catch (Exception e) {
			throw new ConfigurationException("Class with name " + clazzName + " could not be instanciated: " + e.getMessage());
		}
    }

    public static Map<String, Class<? extends Component>> getAvailableTypes(Type componentType){
        return types.get(componentType);
    }

	public static <C extends Component> Optional<C> buildOptional(Type type, Properties props) throws ConfigurationException  {
		if(props == null || !props.isTypeDefined())
			return Optional.empty();
		
		return Optional.of(build(type, props));
	}

}