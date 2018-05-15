package com.hsbc.superfielddemo.superfield

/**
 * Created by zack zeng on 2018/5/15.
 */
interface IValidationStrategy {
    fun validProxyId(input: String): Boolean
}