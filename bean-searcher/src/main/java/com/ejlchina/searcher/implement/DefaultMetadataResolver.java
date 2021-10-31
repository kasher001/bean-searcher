package com.ejlchina.searcher.implement;

import com.ejlchina.searcher.*;
import com.ejlchina.searcher.bean.DbField;
import com.ejlchina.searcher.bean.SearchBean;
import com.ejlchina.searcher.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/***
 * 默认元信息解析器
 * @author Troy.Zhou @ 2021-10-30
 * @since v3.0.0
 */
public class DefaultMetadataResolver implements MetadataResolver {

    private final Map<Class<?>, Metadata<?>> cache = new ConcurrentHashMap<>();

    private EmbedParamResolver embedParamResolver = new DefaultEmbedParamResolver();

    @Override
    public <T> Metadata<T> resolve(Class<T> beanClass) {
        @SuppressWarnings("unchecked")
        Metadata<T> metadata = (Metadata<T>) cache.get(beanClass);
        if (metadata != null) {
            return metadata;
        }
        synchronized (cache) {
            metadata = resolveMetadata(beanClass);
            cache.put(beanClass, metadata);
            return metadata;
        }
    }

    private <T> Metadata<T> resolveMetadata(Class<T> beanClass) {
        SearchBean searchBean = beanClass.getAnnotation(SearchBean.class);
        if (searchBean == null) {
            throw new SearcherException("The class [" + beanClass.getName()
                    + "] is not a valid SearchBean, please check whether the class is annotated correctly by @SearchBean");
        }
        SqlSnippet tableSolution = embedParamResolver.resolve(searchBean.tables());
        SqlSnippet joinCondSolution = embedParamResolver.resolve(searchBean.joinCond());
        SqlSnippet groupBySolution = embedParamResolver.resolve(searchBean.groupBy());

        Metadata<T> metadata = new Metadata<>(beanClass, tableSolution, joinCondSolution, groupBySolution, searchBean.distinct());

        for (Field field : beanClass.getDeclaredFields()) {
            DbField dbField = field.getAnnotation(DbField.class);
            if (dbField == null) {
                continue;
            }
            SqlSnippet solution = embedParamResolver.resolve(dbField.value().trim());
            String snippet = solution.getSqlSnippet();
            if (snippet.toLowerCase().startsWith("select ")) {
                solution.setSqlSnippet("(" + snippet + ")");
            }
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            try {
                Method method = beanClass.getMethod("set" + StringUtils.firstCharToUpperCase(fieldName), fieldType);
                metadata.addFieldDbMap(fieldName, solution, method, fieldType);
            } catch (Exception e) {
                throw new SearcherException("[" + beanClass.getName() + ": " + fieldName + "] is annotated by @DbField, but there is none correctly setter for it.", e);
            }
        }
        if (metadata.getFieldList().size() == 0) {
            throw new SearcherException("[" + beanClass.getName() + "] is annotated by @SearchBean, but there is none field annotated by @DbFile.");
        }
        return metadata;
    }

    public EmbedParamResolver getEmbedParamResolver() {
        return embedParamResolver;
    }

    public void setEmbedParamResolver(EmbedParamResolver embedParamResolver) {
        this.embedParamResolver = Objects.requireNonNull(embedParamResolver);
    }

}
