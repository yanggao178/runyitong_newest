#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快递服务API路由
实现快递查询相关的后端API接口
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Dict, List, Optional
import requests
import json
import os
from datetime import datetime
import logging
import hashlib
import base64  # 添加base64模块
import urllib.parse  # 添加urllib.parse以支持URL编码
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

from database import get_db

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 创建路由器
router = APIRouter()

# 快递鸟API配置
class KuaidiNiaoConfig:
    """
    快递鸟API配置类
    用于管理快递鸟API相关的配置信息
    """
    def __init__(self):
        # 从环境变量或配置文件读取配置
        # 请根据实际情况修改为您的快递鸟API账号信息
        self.app_id = os.getenv('KUAI_DINIAO_APP_ID', '')  # 快递鸟用户ID
        self.api_key = os.getenv('KUAI_DINIAO_API_KEY', '')  # 快递鸟API Key
        self.request_url = os.getenv('KUAI_DINIAO_REQUEST_URL', 'https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx')
        self.query_path = os.getenv('KUAI_DINIAO_QUERY_PATH', '20180801')  # 即时查询接口指令
        self.companies_path = os.getenv('KUAI_DINIAO_COMPANIES_PATH', '2014-06-06')  # 快递公司查询接口指令
    
    def get_app_id(self) -> str:
        """获取快递鸟用户ID"""
        if not self.app_id:
            raise ValueError("快递鸟用户ID未配置，请设置环境变量 KUAI_DINIAO_APP_ID")
        return self.app_id
    
    def get_api_key(self) -> str:
        """获取快递鸟API Key"""
        if not self.api_key:
            raise ValueError("快递鸟API Key未配置，请设置环境变量 KUAI_DINIAO_API_KEY")
        return self.api_key
    
    def get_request_url(self) -> str:
        """获取API请求URL"""
        return self.request_url
    
    def get_query_path(self) -> str:
        """获取查询接口指令"""
        return self.query_path
    
    def get_companies_path(self) -> str:
        """获取快递公司查询接口指令"""
        return self.companies_path

# 全局配置实例
kuaidi_niao_config = KuaidiNiaoConfig()

# 快递公司代码映射
express_companies = {
    "SF": {"code": "SF", "name": "顺丰速运"},
    "YZPY": {"code": "YZPY", "name": "中国邮政"},
    "JD": {"code": "JD", "name": "京东物流"},
    "ZTO": {"code": "ZTO", "name": "中通快递"},
    "YTO": {"code": "YTO", "name": "圆通速递"},
    "YD": {"code": "YD", "name": "韵达快递"},
    "STO": {"code": "STO", "name": "申通快递"},
    "HTKY": {"code": "HTKY", "name": "百世快递"},
    "TTT": {"code": "TTT", "name": "天天快递"},
    "ZJS": {"code": "ZJS", "name": "宅急送"},
    "EMS": {"code": "EMS", "name": "EMS"},
    "YUNDA": {"code": "YD", "name": "韵达快递"},
    "JTSD": {"code": "JTSD", "name": "极兔速递"},
    "YMDD": {"code": "YMDD", "name": "壹米滴答"},
    "QUANFENG": {"code": "QFKD", "name": "全峰快递"},
    "DEPPON": {"code": "DPD", "name": "德邦快递"},
    "SUER": {"code": "SF", "name": "顺丰速运"}
}

def generate_sign(data: str, api_key: str) -> str:
    """
    生成快递鸟API签名（严格按照官方文档要求）
    1. 将请求数据和API密钥拼接
    2. 进行MD5加密（32位小写）
    3. 进行Base64编码
    4. 进行URL编码
    
    :param data: 加密数据
    :param api_key: API密钥
    :return: 签名字符串
    """
    logger.info(f"生成签名 - 原始数据: {data}")
    logger.info(f"生成签名 - 原始API密钥: {api_key}")
    
    # 1. 组合数据（RequestData未编码+ApiKey）
    combined_data = data + api_key
    
    # 2. 进行MD5加密（32位小写）
    md5_hash = hashlib.md5(combined_data.encode('utf-8'))
    md5_hex = md5_hash.hexdigest().lower()
    logger.info(f"生成签名 - MD5小写结果: {md5_hex}")
    
    # 3. 进行Base64编码
    base64_encoded = base64.b64encode(md5_hex.encode('utf-8')).decode('utf-8')
    logger.info(f"生成签名 - Base64编码结果: {base64_encoded}")
    
    # 4. 进行URL编码
    url_encoded_sign = urllib.parse.quote(base64_encoded)
    logger.info(f"生成签名 - URL编码结果: {url_encoded_sign}")
    
    return url_encoded_sign

def query_kuaidi_niao(order_sn: str, shipper_code: str, sender_phone: Optional[str] = None) -> Dict:
    """
    调用快递鸟API查询物流信息
    :param order_sn: 快递单号
    :param shipper_code: 快递公司代码
    :param sender_phone: 寄件人手机号（可选，用于获取后4位作为CustomerName）
    :return: 查询结果
    """
    try:
        # 获取配置
        app_id = kuaidi_niao_config.get_app_id()
        api_key = kuaidi_niao_config.get_api_key()
        request_url = kuaidi_niao_config.get_request_url()
        query_path = kuaidi_niao_config.get_query_path()
        
        # 验证配置格式
        if not app_id.isdigit():
            logger.error(f"快递鸟EBusinessID必须是数字格式: {app_id}")
            return {
                "success": False,
                "message": "快递鸟配置错误：EBusinessID必须是数字格式"
            }
        
        logger.info(f"查询物流 - 快递公司代码: {shipper_code}, 快递单号: {order_sn}")
        logger.info(f"查询物流 - EBusinessID: {app_id}")
        logger.info(f"查询物流 - 请求路径: {query_path}")
        
        # 获取寄件人手机后4位作为CustomerName
        # 如果未提供或格式不正确，使用默认值
        if sender_phone and len(sender_phone) >= 4 and sender_phone.isdigit():
            customer_name = sender_phone[-4:]  # 获取手机后4位
            logger.info(f"使用寄件人手机后4位作为CustomerName: {customer_name}")
        else:
            customer_name = ""  # 默认值
            logger.warning(f"寄件人手机号格式不正确或未提供，使用默认值: {customer_name}")
        
        # 构建请求数据 - 使用寄件人手机后4位作为CustomerName
        request_data = {
            "ShipperCode": shipper_code,
            "LogisticCode": order_sn,
            "CustomerName": customer_name  # 使用寄件人手机后4位
        }
        
        # 将请求数据转换为JSON字符串，确保键值对顺序并使用ASCII编码
        # 快递鸟API要求JSON字符串格式严格一致
        request_data_json = json.dumps(request_data, ensure_ascii=True, sort_keys=True)
        logger.info(f"查询物流 - 请求数据JSON: {request_data_json}")
        
        # 生成签名（使用正确的签名方法）
        sign = generate_sign(request_data_json, api_key)
        
        # 构建请求参数 - RequestData保持原始未编码状态
        params = {
            "RequestData": request_data_json,  # 不进行URL编码
            "EBusinessID": app_id,  # 保持为字符串格式
            "RequestType": query_path,  # 使用1002（标准物流查询接口）
            "DataSign": sign,        # 签名已包含Base64+URL编码
            "DataType": "2"  # JSON格式
        }
        
        logger.info(f"查询物流 - 请求参数: {params}")
        
        # 发送请求 - 使用application/x-www-form-urlencoded格式
        response = requests.post(
            request_url, 
            data=params,
            headers={
                'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8'
            }
        )
        
        # 记录响应
        logger.info(f"查询物流 - 响应状态码: {response.status_code}")
        logger.info(f"查询物流 - 响应内容: {response.text}")
        
        # 解析响应
        try:
            result = response.json()
            
            # 检查响应状态
            if result.get("Success"):
                return {
                    "success": True,
                    "data": {
                        "shipper_code": shipper_code,
                        "order_sn": order_sn,
                        "status": "success",
                        "traces": [{
                            "time": item.get("AcceptTime"),
                            "description": item.get("AcceptStation")
                        } for item in result.get("Traces", [])]
                    },
                    "message": "查询成功"
                }
            else:
                logger.error(f"查询失败 - 原因: {result.get('Reason', '未知错误')}")
                return {
                    "success": False,
                    "message": result.get("Reason", "查询失败")
                }
        except json.JSONDecodeError:
            logger.error(f"响应解析失败，非JSON格式: {response.text}")
            return {
                "success": False,
                "message": f"响应格式错误: {response.text[:100]}..."
            }
            
    except Exception as e:
        logger.error(f"快递查询异常: {str(e)}")
        return {
            "success": False,
            "message": f"查询异常: {str(e)}"
        }

@router.get("/companies")
async def get_express_companies():
    """
    获取快递公司列表
    返回可用的快递公司代码和名称
    """
    try:
        # 返回预设的快递公司列表
        return {
            "success": True,
            "data": express_companies,
            "message": "获取快递公司列表成功"
        }
    except Exception as e:
        logger.error(f"获取快递公司列表异常: {str(e)}")
        raise HTTPException(status_code=500, detail=f"获取快递公司列表失败: {str(e)}")

@router.get("/track")
async def track_express_order(
    order_sn: str = Query(..., description="快递单号"),
    shipper_code: str = Query(..., description="快递公司代码"),
    sender_phone: Optional[str] = Query(None, description="寄件人手机号，用于获取后4位作为CustomerName")
):
    """
    查询快递物流信息
    :param order_sn: 快递单号
    :param shipper_code: 快递公司代码
    :param sender_phone: 寄件人手机号（可选）
    :return: 物流信息
    """
    try:
        # 验证快递公司代码
        if shipper_code not in express_companies:
            raise HTTPException(status_code=400, detail="无效的快递公司代码")
        
        # 调用快递鸟API查询，传递寄件人手机号
        result = query_kuaidi_niao(order_sn, shipper_code, sender_phone)
        
        if result.get("success"):
            return {
                "success": True,
                "data": result.get("data"),
                "message": "查询成功"
            }
        else:
            raise HTTPException(status_code=404, detail=result.get("message", "未找到物流信息"))
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"物流查询异常: {str(e)}")
        raise HTTPException(status_code=500, detail=f"查询失败: {str(e)}")

@router.post("/order/{order_id}/shipping")
async def create_shipping_for_order(
    order_id: str,
    db: Session = Depends(get_db)
):
    """
    为订单创建物流信息
    注意：此接口目前仅为示例，需要根据实际业务需求实现
    """
    try:
        # TODO: 实现订单物流创建逻辑
        return {
            "success": True,
            "data": {
                "order_id": order_id,
                "shipping_status": "created",
                "create_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            },
            "message": "物流信息创建成功"
        }
    except Exception as e:
        logger.error(f"创建物流信息异常: {str(e)}")
        raise HTTPException(status_code=500, detail=f"创建物流信息失败: {str(e)}")