package com.ejlchina.searcher.implement;

import com.ejlchina.searcher.*;
import com.ejlchina.searcher.bean.InheritType;
import com.ejlchina.searcher.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/***
 * 默认元信息解析器
 * @author Troy.Zhou @ 2021-10-30
 * @since v3.0.0
 */
public class DefaultMetaResolver implements MetaResolver {

    private final Map<Class<?>, BeanMeta<?>> cache = new ConcurrentHashMap<>();

    private SnippetResolver snippetResolver = new DefaultSnippetResolver();

    private DbMapping dbMapping;


    public DefaultMetaResolver() {
        this(new DefaultDbMapping());
    }

    public DefaultMetaResolver(DbMapping dbMapping) {
        this.dbMapping = dbMapping;
    }

    @Override
    public <T> BeanMeta<T> resolve(Class<T> beanClass) {
        @SuppressWarnings("unchecked")
        BeanMeta<T> beanMeta = (BeanMeta<T>) cache.get(beanClass);
        if (beanMeta != null) {
            return beanMeta;
        }
        synchronized (cache) {
            beanMeta = resolveMetadata(beanClass);
            cache.put(beanClass, beanMeta);
            return beanMeta;
        }
    }

    protected <T> BeanMeta<T> resolveMetadata(Class<T> beanClass) {
        DbMapping.Table table = dbMapping.table(beanClass);
        if (table == null) {
            throw new SearchException("The class [" + beanClass.getName() + "] can not be searched, because it can not be resolved by " + dbMapping.getClass());
        }
        BeanMeta<T> beanMeta = new BeanMeta<>(beanClass, table.getDataSource(),
                snippetResolver.resolve(table.getTables()),
                snippetResolver.resolve(table.getJoinCond()),
                snippetResolver.resolve(table.getGroupBy()),
                table.isDistinct());
        // 字段解析
        Field[] fields = getBeanFields(beanClass);
        for (int index = 0; index < fields.length; index++) {
            Field field = fields[index];
            DbMapping.Column column = dbMapping.column(fields[index]);
            if (column == null) {
                continue;
            }
            FieldMeta fieldMeta = resolveFieldMeta(beanMeta, column, field, index);
            beanMeta.addFieldMeta(field.getName(), fieldMeta);
        }
        if (beanMeta.getFieldCount() == 0) {
            throw new SearchException("[" + beanClass.getName() + "] is not a valid SearchBean, because there is no field mapping to database.");
        }
        return beanMeta;
    }

    protected Field[] getBeanFields(Class<?> beanClass) {
        InheritType iType = dbMapping.inheritType(beanClass);
        List<Field> fieldList = new ArrayList<>();
        while (beanClass != Object.class) {
            for (Field field : beanClass.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    fieldList.add(field);
                }
            }
            if (iType != InheritType.FIELD && iType != InheritType.ALL) {
                break;
            }
            beanClass = beanClass.getSuperclass();
        }
        return fieldList.toArray(new Field[0]);
    }

    protected FieldMeta resolveFieldMeta(BeanMeta<?> beanMeta, DbMapping.Column column, Field field, int index) {
        Method setter = getSetterMethod(beanMeta.getBeanClass(), field);
        SqlSnippet snippet = snippetResolver.resolve(column.getFieldSql());
        // 注意：Oracle 数据库的别名不能以下划线开头
        return new FieldMeta(beanMeta, field, setter, snippet, "c_" + index, column.isConditional(), column.getOnlyOn());
    }

    protected Method getSetterMethod(Class<?> beanClass, Field field) {
        String fieldName = field.getName();
        try {
            return beanClass.getMethod("set" + StringUtils.firstCharToUpperCase(fieldName), field.getType());
        } catch (Exception e) {
            throw new SearchException("[" + beanClass.getName() + ": " + fieldName + "] is annotated by @DbField, but there is no correctly setter for it.", e);
        }
    }

    public SnippetResolver getSnippetResolver() {
        return snippetResolver;
    }

    public void setSnippetResolver(SnippetResolver snippetResolver) {
        this.snippetResolver = Objects.requireNonNull(snippetResolver);
    }

    public DbMapping getDbMapping() {
        return dbMapping;
    }

    public void setDbMapping(DbMapping dbMapping) {
        this.dbMapping = Objects.requireNonNull(dbMapping);
    }

}
