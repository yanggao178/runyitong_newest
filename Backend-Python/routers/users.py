import traceback
from fastapi import APIRouter, Depends, HTTPException, status, Form
from fastapi.responses import JSONResponse
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from jose import JWTError, jwt
from datetime import datetime, timedelta
from typing import Optional
import traceback
from database import get_db
from models import User as UserModel, HealthRecord as HealthRecordModel, IdentityVerification as IdentityVerificationModel
from schemas import User, UserCreate, UserUpdate, HealthRecord, HealthRecordCreate, SmsCodeResponse, SmsRegisterRequest, RegisterResponse, SmsCodeRequest, LoginRequest, SmsLoginRequest, IdentityVerificationCreateRequest, IdentityVerificationResult, IdentityVerificationResponse
import os
import base64
import hashlib
from cryptography.fernet import Fernet

router = APIRouter()

# 密码处理类 - 使用Python内置hashlib，完全不依赖passlib和bcrypt
class PasswordHandler:
    # 用于密码哈希的盐值，生产环境应使用更强的随机盐值存储机制
    SALT_LENGTH = 32
    
    @staticmethod
    def truncate_password(password):
        """截断密码到72字节，考虑多字节字符"""
        if not password:
            return password
        # 确保密码不超过72字节
        byte_length = len(password.encode())
        if byte_length > 72:
            # 安全地截断到72字节
            truncated = password.encode()[:72].decode('utf-8', errors='replace')
            return truncated
        return password
    
    @staticmethod
    def generate_salt():
        """生成随机盐值"""
        return os.urandom(PasswordHandler.SALT_LENGTH)
    
    @staticmethod
    def get_password_hash(password):
        """使用sha256生成密码哈希，完全避免passlib和bcrypt依赖"""
        try:
            # 先截断密码
            truncated = PasswordHandler.truncate_password(password)
            
            # 生成盐值
            salt = PasswordHandler.generate_salt()
            
            # 对于超长密码，我们使用原始密码的哈希作为额外输入
            # 这样即使原始密码被截断，不同的超长密码也能产生不同的哈希值
            if len(password.encode()) > 72:
                # 计算原始密码的哈希
                orig_password_hash = hashlib.sha256(password.encode()).digest()
                # 组合截断后的密码、原始密码哈希和盐值
                combined = truncated.encode() + orig_password_hash + salt
            else:
                # 对于正常长度的密码，只组合密码和盐值
                combined = truncated.encode() + salt
            
            # 使用sha256生成哈希
            hashed = hashlib.sha256(combined).digest()
            
            # 将盐值和哈希结果组合并进行base64编码
            # 格式: salt + hashed -> base64
            combined_result = salt + hashed
            return base64.b64encode(combined_result).decode()
        except Exception:
            # 如果处理失败，抛出异常
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="密码处理失败"
            )
    
    @staticmethod
    def verify_password(plain_password, hashed_password):
        """验证密码，使用sha256，完全避免passlib和bcrypt依赖"""
        try:
            # 先截断密码
            truncated = PasswordHandler.truncate_password(plain_password)
            
            # 解码哈希值
            combined_result = base64.b64decode(hashed_password)
            
            # 提取盐值和存储的哈希
            salt = combined_result[:PasswordHandler.SALT_LENGTH]
            stored_hash = combined_result[PasswordHandler.SALT_LENGTH:]
            
            # 对于超长密码，我们使用原始密码的哈希作为额外输入
            # 这样即使原始密码被截断，不同的超长密码也能产生不同的哈希值
            if plain_password and len(plain_password.encode()) > 72:
                # 计算原始密码的哈希
                orig_password_hash = hashlib.sha256(plain_password.encode()).digest()
                # 组合截断后的密码、原始密码哈希和盐值
                combined = truncated.encode() + orig_password_hash + salt
            else:
                # 对于正常长度的密码，只组合密码和盐值
                combined = truncated.encode() + salt
            
            # 组合密码和盐值并计算哈希
            new_hash = hashlib.sha256(combined).digest()
            
            # 比较哈希值
            # 使用常数时间比较防止计时攻击
            if len(new_hash) != len(stored_hash):
                return False
            
            result = 0
            for x, y in zip(new_hash, stored_hash):
                result |= x ^ y
            return result == 0
        except Exception:
            # 记录异常但不暴露具体错误
            # 验证失败时返回False
            return False

    @staticmethod
    def is_bcrypt_hash(password_hash):
        """检测密码哈希是否是bcrypt格式，用于兼容旧哈希值"""
        # bcrypt哈希通常以$2b$或$2a$开头，长度为60个字符
        return (isinstance(password_hash, str) and 
                len(password_hash) == 60 and 
                password_hash.startswith(('$2b$', '$2a$')))

def generate_jwt_secret():
    # 生成 32 字节的随机数据
    random_bytes = os.urandom(32)
    # 转换为 base64 字符串
    secret_key = base64.b64encode(random_bytes).decode('utf-8')
    return secret_key

# JWT配置
SECRET_KEY = "a5gV+Zsx0P27rOou8YMRJqDWxwq/51b5hsgIntCXZueZa66B/Qx5u3pSmlpC5BLUCT6gxx5Pf3nhOkVxdo7OMA=="  # 在生产环境中应该使用环境变量
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60*24

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/users/token")

# 使用PasswordHandler类提供的方法
def verify_password(plain_password, hashed_password, user=None, db=None):
    # 检查是否为旧的bcrypt哈希格式
    if PasswordHandler.is_bcrypt_hash(hashed_password):
        # 由于passlib会导致错误，我们不再尝试验证旧的bcrypt哈希值
        # 对于旧用户，我们需要提供一个密码重置机制
        print(f"检测到旧格式bcrypt哈希值，但无法验证。请用户重置密码。")
        # 抛出特定的HTTPException，告知用户需要重置密码
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="您的账号使用的是旧密码格式，请使用'忘记密码'功能重置密码后再登录",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    # 对于新的哈希格式，使用我们的SHA256实现
    return PasswordHandler.verify_password(plain_password, hashed_password)

def get_password_hash(password):
    # 生成新的SHA256哈希值
    return PasswordHandler.get_password_hash(password)

# 加密工具函数 - 用于敏感数据（如身份证号）的加密和解密
def generate_encryption_key():
    # 在实际生产环境中，密钥应该从环境变量或安全的密钥管理服务中获取
    # 这里为了演示，使用基于SECRET_KEY生成的密钥
    key_hash = hashlib.sha256(SECRET_KEY.encode()).digest()
    return base64.urlsafe_b64encode(key_hash)

# 获取加密密钥
ENCRYPTION_KEY = generate_encryption_key()
fernet = Fernet(ENCRYPTION_KEY)

def encrypt_sensitive_data(data):
    """加密敏感数据"""
    return fernet.encrypt(data.encode()).decode()

def decrypt_sensitive_data(encrypted_data):
    """解密敏感数据"""
    return fernet.decrypt(encrypted_data.encode()).decode()

# JWT工具函数
def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

# 获取当前用户
async def get_current_user(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = str(payload.get("sub")) if payload.get("sub") else ""
        if username is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    
    user = db.query(UserModel).filter(UserModel.username == username).first()
    if user is None:
        raise credentials_exception
    return user

# 存储验证码的临时字典（生产环境应使用Redis）
verification_codes = {}

# 仅发送验证码（不创建用户）
@router.post("/send-verification-code")
async def send_verification_code_only(
    request: SmsCodeRequest,
    db: Session = Depends(get_db)
):
    """仅发送验证码，不创建用户"""
    import random
    import time
    import os
    from alibabacloud_tea_openapi import models as open_api_models
    from alibabacloud_dysmsapi20170525.client import Client as Dysmsapi20170525Client
    from alibabacloud_dysmsapi20170525 import models as dysmsapi_20170525_models
    from alibabacloud_tea_util import models as util_models
    
    phone = request.phone
    # 检查手机号格式
    if not phone or len(phone) != 11 or not phone.startswith('1'):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    
    # 生成6位验证码
    verification_code = str(random.randint(100000, 999999))
    
    # 存储验证码（5分钟过期）
    verification_codes[phone] = {
        "code": verification_code,
        "expires_at": time.time() + 300  # 5分钟后过期
    }
    
    try:
        # 从环境变量获取阿里云短信服务配置
        access_key_id = os.getenv('ALIYUN_SMS_ACCESS_KEY_ID', 'LTAI5tBuCzPHCNmFV2iZZfyD')
        access_key_secret = os.getenv('ALIYUN_SMS_ACCESS_KEY_SECRET', 'Y4A4cFSPPk99w8zxDu34rgMf6h4Cp5')
        sign_name = os.getenv('ALIYUN_SMS_SIGN_NAME', '河北稳行科技')
        template_code = os.getenv('ALIYUN_SMS_TEMPLATE_CODE', 'SMS_325990524')
        
        # 配置阿里云客户端
        config = open_api_models.Config(
            access_key_id=access_key_id,
            access_key_secret=access_key_secret
        )
        config.endpoint = 'dysmsapi.aliyuncs.com'
        
        # 创建短信客户端
        client = Dysmsapi20170525Client(config)
        
        # 构造短信发送请求
        send_sms_request = dysmsapi_20170525_models.SendSmsRequest(
            phone_numbers=phone,
            sign_name=sign_name,
            template_code=template_code,
            template_param=f"{{\"code\":\"{verification_code}\"}}"
        )
        
        # 发送短信
        runtime = util_models.RuntimeOptions()
        response = client.send_sms_with_options(send_sms_request, runtime)
        
        # 检查发送结果
        if response.body.code != 'OK':
            raise HTTPException(status_code=500, detail=f"短信发送失败: {response.body.message}")
        
        # 记录发送日志
        print(f"阿里云短信发送成功: {phone}, 验证码: {verification_code}")
        
        return {
            "success": True,
            "message": "验证码发送成功",
            "data": {
                "phone": phone,
                "message": "验证码发送成功",
                "expires_in": 300
            }
        }
        
    except Exception as e:
        # 如果发送失败，从验证码字典中删除该记录
        if phone in verification_codes:
            del verification_codes[phone]
        # 如果不是HTTPException，则包装成HTTPException
        if not isinstance(e, HTTPException):
            print(f"发生未预期的错误: {e}")
            print("详细错误信息:")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=f"短信发送失败: {str(e)}")
        else:
            raise e

# 发送短信验证码（带用户创建）
@router.post("/send-sms-code")
async def send_sms_code(request: SmsCodeRequest, db: Session = Depends(get_db)):
    """发送短信验证码"""
    import random
    import time
    import os
    from alibabacloud_tea_openapi import models as open_api_models
    from alibabacloud_dysmsapi20170525.client import Client as Dysmsapi20170525Client
    from alibabacloud_dysmsapi20170525 import models as dysmsapi_20170525_models
    from alibabacloud_tea_util import models as util_models
    
    phone = request.phone
    # 检查手机号格式
    if not phone or len(phone) != 11 or not phone.startswith('1'):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    
    # 生成6位验证码
    verification_code = str(random.randint(100000, 999999))
    
    # 存储验证码（5分钟过期）
    verification_codes[phone] = {
        "code": verification_code,
        "expires_at": time.time() + 300  # 5分钟后过期
    }
    
    try:
        # 从环境变量获取阿里云短信服务配置
        access_key_id = os.getenv('ALIYUN_SMS_ACCESS_KEY_ID', 'LTAI5tBuCzPHCNmFV2iZZfyD')
        access_key_secret = os.getenv('ALIYUN_SMS_ACCESS_KEY_SECRET', 'Y4A4cFSPPk99w8zxDu34rgMf6h4Cp5')
        sign_name = os.getenv('ALIYUN_SMS_SIGN_NAME', '河北稳行科技')
        template_code = os.getenv('ALIYUN_SMS_TEMPLATE_CODE', 'SMS_325990524')
        
        # 配置阿里云客户端
        config = open_api_models.Config(
            access_key_id=access_key_id,
            access_key_secret=access_key_secret
        )
        config.endpoint = 'dysmsapi.aliyuncs.com'
        
        # 创建短信客户端
        client = Dysmsapi20170525Client(config)
        
        # 构造短信发送请求
        send_sms_request = dysmsapi_20170525_models.SendSmsRequest(
            phone_numbers=phone,
            sign_name=sign_name,
            template_code=template_code,
            template_param=f"{{\"code\":\"{verification_code}\"}}"
        )
        
        # 发送短信
        runtime = util_models.RuntimeOptions()
        response = client.send_sms_with_options(send_sms_request, runtime)
        
        # 检查发送结果
        if response.body.code != 'OK':
            raise HTTPException(status_code=500, detail=f"短信发送失败: {response.body.message}")
        
        # 记录发送日志
        print(f"阿里云短信发送成功: {phone}, 验证码: {verification_code}")
        
        # 检查该手机号是否已存在于数据库中
        db_user = db.query(UserModel).filter(UserModel.phone == phone).first()
        if not db_user:
            # 如果手机号不存在，创建新用户
            # 生成临时密码（使用手机号后6位作为初始密码）
            temp_password = phone[-6:] if len(phone) >= 6 else ''
            hashed_password = get_password_hash(temp_password)

            # 检查用户名是否已存在，如果存在则添加时间戳
            username = f"user_{phone}"
            existing_user = db.query(UserModel).filter(UserModel.username == username).first()
            if existing_user:
                username = f"user_{phone}_{int(time.time())}"

            # 创建新用户对象
            new_user = UserModel(
                username=username,  # 使用手机号生成用户名，如果已存在则添加时间戳
                email=None,  # 短信发送时不设置邮箱
                phone=phone,
                hashed_password=hashed_password,
                avatar_url=None,
                is_active=True,
                full_name=f"用户{phone[-4:]}",  # 使用手机号后4位作为昵称
                created_time=datetime.now(),
                updated_time=datetime.now()
            )

            try:
                # 保存到数据库
                print(f"开始添加用户到数据库: {username}")
                db.add(new_user)

                print("开始提交事务...")
                db.commit()

                print("提交成功，开始刷新...")
                db.refresh(new_user)

                print(f"新用户创建成功: {phone}, 用户ID: {new_user.id}")
            except Exception as db_error:
                # 如果数据库操作失败，回滚事务
                print(f"数据库操作失败: {db_error}")
                # 从验证码字典中删除该记录
                if phone in verification_codes:
                    del verification_codes[phone]
                raise HTTPException(status_code=500, detail=f"用户创建失败: {str(db_error)}")
        else:
            print(f"用户已存在: {phone}")
        
    except Exception as e:
        # 如果发送失败，从验证码字典中删除该记录
        if phone in verification_codes:
            del verification_codes[phone]
        # 如果不是HTTPException，则包装成HTTPException
        if not isinstance(e, HTTPException):
            print(f"发生未预期的错误: {e}")
            print("详细错误信息:")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail=f"短信发送失败: {str(e)}")
        else:
            raise e
    
    return {
        "success": True,
        "message": "验证码发送成功",
        "data": {
            "phone": phone,
            "message": "验证码发送成功",
            "expires_in": 300
        }
    }

# 短信注册
@router.post("/register-with-sms")
async def register_with_sms(
    username: str = Form(...),
    phone: str = Form(...),
    verification_code: str = Form(...),
    password: str = Form(...),
    db: Session = Depends(get_db)
):
    """短信验证码注册"""
    import time

    # 验证验证码
    if phone not in verification_codes:
        raise HTTPException(status_code=400, detail="验证码不存在或已过期")

    stored_code_info = verification_codes[phone]
    if time.time() > stored_code_info["expires_at"]:
        del verification_codes[phone]
        raise HTTPException(status_code=400, detail="验证码已过期")

    if stored_code_info["code"] != verification_code:
        raise HTTPException(status_code=400, detail="验证码错误")

    # 检查用户名是否已存在
    db_user = db.query(UserModel).filter(UserModel.username == username).first()
    if db_user:
        raise HTTPException(status_code=400, detail="用户名已存在")

    # 检查手机号是否已存在
    db_user = db.query(UserModel).filter(UserModel.phone == phone).first()
    if db_user:
        raise HTTPException(status_code=400, detail="手机号已被注册")

    # 验证手机号格式
    if not phone or len(phone) != 11 or not phone.startswith('1'):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    
    # 验证密码强度
    if not password or len(password) < 6:
        raise HTTPException(status_code=400, detail="密码长度不能少于6位")

    # 创建新用户
    hashed_password = get_password_hash(password)
    db_user = UserModel(
        username=str(username),
        email=None,  # 短信注册可以不填邮箱
        full_name=None,
        phone=str(phone),
        avatar_url=None,
        hashed_password=str(hashed_password),
        is_active=True,  # 确保新用户是激活状态
        created_time=datetime.now(),
        updated_time=datetime.now()
    )
    try:
        db.add(db_user)
        db.commit()
        db.refresh(db_user)
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"创建用户失败: {str(e)}")

    # 删除已使用的验证码
    # del verification_codes[phone]

    # 使用JSONResponse确保返回正确的格式
    user_id = getattr(db_user, 'id', 0)
    user_username = getattr(db_user, 'username', '')
    user_phone = getattr(db_user, 'phone', None)
    user_email = getattr(db_user, 'email', None)
    user_full_name = getattr(db_user, 'full_name', None)
    
    response_data = {
        "success": True,
        "message": "注册成功",
        "data": {
            "user_id": int(user_id) if user_id is not None else 0,
            "username": str(user_username) if user_username is not None else "",
            "phone": str(user_phone) if user_phone is not None else None,
            "email": str(user_email) if user_email is not None else None,
            "full_name": str(user_full_name) if user_full_name is not None else None
        }
    }
    return JSONResponse(content=response_data, status_code=200)

# 短信登录
@router.post("/login-with-sms")
async def login_with_sms(request: SmsLoginRequest, db: Session = Depends(get_db)):
    """短信验证码登录"""
    import time
    
    phone = request.phone
    verification_code = request.verification_code
    
    # 验证手机号格式
    if not phone or len(phone) != 11 or not phone.startswith('1'):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    
    # 验证验证码
    if phone not in verification_codes:
        raise HTTPException(status_code=400, detail="验证码不存在或已过期")
    
    stored_code_info = verification_codes[phone]
    if time.time() > stored_code_info["expires_at"]:
        del verification_codes[phone]
        raise HTTPException(status_code=400, detail="验证码已过期")
    
    if stored_code_info["code"] != verification_code:
        raise HTTPException(status_code=400, detail="验证码错误")
    
    # 查找用户
    db_user = db.query(UserModel).filter(UserModel.phone == phone).first()
    if not db_user:
        raise HTTPException(status_code=401, detail="该手机号未注册")
    
    # 检查用户是否激活
    if not bool(getattr(db_user, 'is_active', False)):
        raise HTTPException(status_code=401, detail="用户账号已被禁用")
    
    # 生成访问令牌
    access_token_expires = timedelta(minutes=int(ACCESS_TOKEN_EXPIRE_MINUTES))
    access_token = create_access_token(
        data={"sub": str(getattr(db_user, 'username', ''))}, expires_delta=access_token_expires
    )
    
    # 删除已使用的验证码
    del verification_codes[phone]
    
    # 使用JSONResponse确保返回正确的格式
    user_id = getattr(db_user, 'id', 0)
    user_username = getattr(db_user, 'username', '')
    user_phone = getattr(db_user, 'phone', None)
    user_email = getattr(db_user, 'email', None)
    user_full_name = getattr(db_user, 'full_name', None)
    user_avatar_url = getattr(db_user, 'avatar_url', None)
    
    return {
        "success": True,
        "message": "登录成功",
        "data": {
            "user_id": int(user_id) if user_id is not None else 0,
            "username": str(user_username) if user_username is not None else "",
            "phone": str(user_phone) if user_phone is not None else None,
            "email": str(user_email) if user_email is not None else None,
            "full_name": str(user_full_name) if user_full_name is not None else None,
            "avatar_url": str(user_avatar_url) if user_avatar_url is not None else None,
            "access_token": access_token,
            "token_type": "bearer"
        }
    }


# 密码重置请求验证
@router.post("/reset-password-request")
async def reset_password_request(
    request: SmsCodeRequest,
    db: Session = Depends(get_db)
):
    """发送密码重置验证码"""
    import random
    import time
    import os
    from alibabacloud_tea_openapi import models as open_api_models
    from alibabacloud_dysmsapi20170525.client import Client as Dysmsapi20170525Client
    from alibabacloud_dysmsapi20170525 import models as dysmsapi_20170525_models
    from alibabacloud_tea_util import models as util_models
    
    phone = request.phone
    # 检查手机号格式
    if not phone or len(phone) != 11 or not phone.startswith('1'):
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    
    # 检查手机号是否已注册
    user = db.query(UserModel).filter(UserModel.phone == phone).first()
    if not user:
        raise HTTPException(status_code=404, detail="该手机号未注册")
    
    # 生成6位验证码
    verification_code = str(random.randint(100000, 999999))
    
    # 存储验证码（5分钟过期），标记为重置密码用途
    verification_codes[phone] = {
        "code": verification_code,
        "expires_at": time.time() + 300,  # 5分钟后过期
        "purpose": "reset_password"
    }
    
    try:
        # 从环境变量获取阿里云短信服务配置
        access_key_id = os.getenv('ALIYUN_SMS_ACCESS_KEY_ID', 'LTAI5tBuCzPHCNmFV2iZZfyD')
        access_key_secret = os.getenv('ALIYUN_SMS_ACCESS_KEY_SECRET', 'Y4A4cFSPPk99w8zxDu34rgMf6h4Cp5')
        sign_name = os.getenv('ALIYUN_SMS_SIGN_NAME', '河北稳行科技')
        # 使用密码重置专用的短信模板
        template_code = os.getenv('ALIYUN_SMS_RESET_PASSWORD_TEMPLATE', 'SMS_325990524')
        
        # 配置阿里云客户端
        config = open_api_models.Config(
            access_key_id=access_key_id,
            access_key_secret=access_key_secret
        )
        config.endpoint = 'dysmsapi.aliyuncs.com'
        
        # 创建短信客户端
        client = Dysmsapi20170525Client(config)
        
        # 构造短信发送请求
        send_sms_request = dysmsapi_20170525_models.SendSmsRequest(
            phone_numbers=phone,
            sign_name=sign_name,
            template_code=template_code,
            template_param=f"{{\"code\":\"{verification_code}\"}}"
        )
        
        # 发送短信
        runtime = util_models.RuntimeOptions()
        response = client.send_sms_with_options(send_sms_request, runtime)
        
        # 记录日志但不暴露验证码
        print(f"向手机号 {phone} 发送密码重置验证码")
        
        return {"success": True, "message": "验证码已发送，请查收"}
    except Exception as e:
        # 记录错误但不暴露详细信息
        print(f"发送验证码失败: {str(e)}")
        # 即使发送失败也返回成功消息，避免暴露系统问题
        return {"success": True, "message": "验证码已发送，请查收"}

# 密码重置
@router.post("/reset-password")
async def reset_password(
    phone: str = Form(...),
    verification_code: str = Form(...),
    new_password: str = Form(...),
    db: Session = Depends(get_db)
):
    """使用验证码重置密码"""
    import time

    # 验证验证码
    if phone not in verification_codes:
        raise HTTPException(status_code=400, detail="验证码不存在或已过期")

    stored_code_info = verification_codes[phone]
    # 检查验证码是否过期
    if time.time() > stored_code_info["expires_at"]:
        del verification_codes[phone]
        raise HTTPException(status_code=400, detail="验证码已过期")

    # 检查验证码是否正确
    if stored_code_info["code"] != verification_code:
        raise HTTPException(status_code=400, detail="验证码错误")
    
    # 检查验证码用途
    if "purpose" in stored_code_info and stored_code_info["purpose"] != "reset_password":
        raise HTTPException(status_code=400, detail="验证码用途不匹配")

    # 查找用户
    user = db.query(UserModel).filter(UserModel.phone == phone).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    # 验证新密码强度
    if not new_password or len(new_password) < 6:
        raise HTTPException(status_code=400, detail="密码长度至少为6位")
    
    # 更新密码为新的SHA256哈希格式
    user.hashed_password = get_password_hash(new_password)
    
    # 保存更改
    db.commit()
    
    # 删除已使用的验证码
    del verification_codes[phone]
    
    return {"success": True, "message": "密码重置成功，请使用新密码登录"}
@router.post("/register")
async def register_user(user: UserCreate, db: Session = Depends(get_db)):
    """用户注册"""
    # 检查用户名是否已存在
    db_user = db.query(UserModel).filter(UserModel.username == user.username).first()
    if db_user:
        raise HTTPException(status_code=400, detail="用户名已存在")
    
    # 创建新用户
    hashed_password = get_password_hash(user.password)
    db_user = UserModel(
        username=user.username,
        email=None,  # 始终设置为None
        full_name=user.full_name,
        phone=user.phone,
        avatar_url=user.avatar_url,
        hashed_password=hashed_password
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    
    # 保存用户信息到变量，避免直接引用数据库对象
    user_id = db_user.id
    username = db_user.username
    phone = db_user.phone
    email = None  # 不再返回邮箱
    full_name = db_user.full_name
    
    # 使用JSONResponse确保返回正确的格式
    from fastapi.responses import JSONResponse
    response_data = {
        "success": True,
        "message": "注册成功",
        "data": {
            "user_id": user_id,
            "username": username,
            "phone": phone,
            "full_name": full_name
        }
    }
    return JSONResponse(content=response_data, status_code=200, headers={"Content-Type": "application/json"})

# 测试端点
@router.get("/debug/test-json-response")
async def test_json_response():
    """测试JSON响应格式"""
    from fastapi.responses import JSONResponse
    response_data = {
        "success": True,
        "message": "测试JSON响应成功",
        "data": {
            "user_id": 999,
            "username": "testuser",
            "email": "test@example.com"
        }
    }
    return JSONResponse(content=response_data, status_code=200)

# 用户登录
@router.post("/token")
async def login_for_access_token(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    """用户登录获取访问令牌"""
    user = db.query(UserModel).filter(UserModel.username == form_data.username).first()
    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="用户名或密码错误",
            headers={"WWW-Authenticate": "Bearer"},
        )
    access_token_expires = timedelta(minutes=int(ACCESS_TOKEN_EXPIRE_MINUTES))
    access_token = create_access_token(
        data={"sub": user.username}, expires_delta=access_token_expires
    )
    return {"access_token": access_token, "token_type": "bearer"}

# 简单用户登录接口（用于Android客户端）
@router.post("/login")
async def login_user(request: LoginRequest, db: Session = Depends(get_db)):
    """用户登录验证"""
    username = request.username
    password = request.password
    
    # 查找用户
    user = db.query(UserModel).filter(UserModel.username == username).first()
    print(user)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="用户名不存在"
        )

    # 验证密码
    if not verify_password(password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="密码错误"
        )
    
    # 检查用户是否激活
    if not bool(getattr(user, 'is_active', False)):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="用户账号已被禁用"
        )
    
    # 生成访问令牌
    access_token_expires = timedelta(minutes=int(ACCESS_TOKEN_EXPIRE_MINUTES))
    access_token = create_access_token(
        data={"sub": getattr(user, 'username', '')}, expires_delta=access_token_expires
    )
    
    user_id = getattr(user, 'id', 0)
    user_username = getattr(user, 'username', '')
    user_email = getattr(user, 'email', None)
    user_full_name = getattr(user, 'full_name', None)
    user_phone = getattr(user, 'phone', None)
    user_avatar_url = getattr(user, 'avatar_url', None)
    
    return {
        "success": True,
        "message": "登录成功",
        "data": {
            "user_id": int(user_id) if user_id is not None else 0,
            "username": str(user_username) if user_username is not None else "",
            "email": str(user_email) if user_email is not None else None,
            "full_name": str(user_full_name) if user_full_name is not None else None,
            "phone": str(user_phone) if user_phone is not None else None,
            "avatar_url": str(user_avatar_url) if user_avatar_url is not None else None,
            "access_token": access_token,
            "token_type": "bearer"
        }
    }

# 获取当前用户信息
@router.get("/me", response_model=User)
async def read_users_me(current_user: UserModel = Depends(get_current_user)):
    """获取当前用户信息"""
    return current_user

# 更新用户信息
@router.put("/me", response_model=User)
async def update_user_me(
    user_update: UserUpdate,
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """更新当前用户信息"""
    update_data = user_update.dict(exclude_unset=True)
    
    # 如果更新密码，需要加密
    if "password" in update_data:
        update_data["hashed_password"] = get_password_hash(update_data.pop("password"))
    
    # 检查用户名和邮箱唯一性
    if "username" in update_data and update_data["username"] != current_user.username:
        existing_user = db.query(UserModel).filter(
            UserModel.username == update_data["username"],
            UserModel.id != current_user.id
        ).first()
        if existing_user:
            raise HTTPException(status_code=400, detail="用户名已存在")
    
    # 更新用户信息
    for field, value in update_data.items():
        setattr(current_user, field, value)
    
    db.commit()
    db.refresh(current_user)
    return current_user

# 测试注册端点
@router.post("/test-register-new")
async def test_register_new():
    """测试注册响应格式"""
    from fastapi.responses import JSONResponse
    response_data = {
        "success": True,
        "message": "测试注册成功",
        "data": {
            "user_id": 999,
            "username": "test_user",
            "phone": "13800138000",
            "email": "test@test.com",
            "full_name": "测试用户"
        }
    }
    return JSONResponse(content=response_data, status_code=200, headers={"Content-Type": "application/json"})

# 获取用户详情（管理员功能）
@router.get("/{user_id}", response_model=User)
async def get_user(user_id: int, db: Session = Depends(get_db)):
    """获取用户详情"""
    user = db.query(UserModel).filter(UserModel.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    return user

# 创建健康档案记录
@router.post("/me/health-records", response_model=HealthRecord)
async def create_health_record(
    health_record: HealthRecordCreate,
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """创建健康档案记录"""
    db_record = HealthRecordModel(
        user_id=current_user.id,
        **health_record.dict()
    )
    db.add(db_record)
    db.commit()
    db.refresh(db_record)
    return db_record

# 获取用户健康档案
@router.get("/me/health-records")
async def get_my_health_records(
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """获取当前用户的健康档案"""
    records = db.query(HealthRecordModel).filter(
        HealthRecordModel.user_id == current_user.id
    ).order_by(HealthRecordModel.recorded_date.desc()).all()
    
    return {"health_records": records}

# 获取用户统计信息
@router.get("/me/stats")
async def get_user_stats(
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """获取用户统计信息"""
    from models import Appointment as AppointmentModel, Prescription as PrescriptionModel
    
    # 统计预约数量
    total_appointments = db.query(AppointmentModel).filter(
        AppointmentModel.user_id == current_user.id
    ).count()
    
    pending_appointments = db.query(AppointmentModel).filter(
        AppointmentModel.user_id == current_user.id,
        AppointmentModel.status == "pending"
    ).count()
    
    # 统计处方数量
    total_prescriptions = db.query(PrescriptionModel).filter(
        PrescriptionModel.user_id == current_user.id
    ).count()
    
    # 统计健康记录数量
    total_health_records = db.query(HealthRecordModel).filter(
        HealthRecordModel.user_id == current_user.id
    ).count()
    
    return {
        "user_id": current_user.id,
        "username": current_user.username,
        "stats": {
            "total_appointments": total_appointments,
            "pending_appointments": pending_appointments,
            "total_prescriptions": total_prescriptions,
            "total_health_records": total_health_records
        }
    }

# 删除健康记录
@router.delete("/me/health-records/{record_id}")
async def delete_health_record(
    record_id: int,
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """删除健康记录"""
    record = db.query(HealthRecordModel).filter(
        HealthRecordModel.id == record_id,
        HealthRecordModel.user_id == current_user.id
    ).first()
    
    if not record:
        raise HTTPException(status_code=404, detail="健康记录不存在")
    
    db.delete(record)
    db.commit()
    return {"message": "健康记录删除成功"}

# 修改密码
@router.post("/me/change-password")
async def change_password(
    old_password: str,
    new_password: str,
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """修改密码"""
    # 验证旧密码
    if not verify_password(old_password, current_user.hashed_password):
        raise HTTPException(status_code=400, detail="原密码错误")
    
    # 更新密码
    setattr(current_user, 'hashed_password', get_password_hash(new_password))
    db.commit()
    
    return {"message": "密码修改成功"}

# 创建或更新实名认证信息
@router.post("/me/identity-verification", response_model=IdentityVerificationResult)
async def create_identity_verification(
    verification_data: IdentityVerificationCreateRequest,
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """创建或更新用户实名认证信息"""
    # 检查用户是否已提交实名认证
    existing_verification = db.query(IdentityVerificationModel).filter(
        IdentityVerificationModel.user_id == current_user.id
    ).first()
    
    # 加密身份证号
    encrypted_id_card = encrypt_sensitive_data(verification_data.id_card_number)
    
    if existing_verification:
        # 更新现有实名认证信息
        setattr(existing_verification, 'real_name', verification_data.real_name)
        setattr(existing_verification, 'id_card_number', encrypted_id_card)
        setattr(existing_verification, 'status', "pending")
        setattr(existing_verification, 'updated_at', datetime.now())
        db.commit()
        db.refresh(existing_verification)
        verification = existing_verification
    else:
        # 创建新的实名认证记录
        verification = IdentityVerificationModel(
            user_id=current_user.id,
            real_name=verification_data.real_name,
            id_card_number=encrypted_id_card,
            status="pending",
            created_at=datetime.now(),
            updated_at=datetime.now()
        )
        db.add(verification)
        db.commit()
        db.refresh(verification)
    
    # 构建响应对象，注意不要返回完整的身份证号
    verification_response = IdentityVerificationResponse(
        id=int(getattr(verification, 'id', 0)),
        user_id=int(getattr(verification, 'user_id', 0)),
        real_name=str(getattr(verification, 'real_name', '')),
        id_card_number="****" + verification_data.id_card_number[-4:],  # 只显示最后4位
        status=str(getattr(verification, 'status', '')),
        verification_time=getattr(verification, 'verification_time', None),
        created_at=getattr(verification, 'created_at', datetime.now()),
        updated_at=getattr(verification, 'updated_at', datetime.now())
    )
    
    return {
        "success": True,
        "message": "认证信息已提交，等待审核",
        "data": verification_response
    }

# 获取实名认证状态
@router.get("/me/identity-verification", response_model=IdentityVerificationResult)
async def get_identity_verification_status(
    current_user: UserModel = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """获取用户实名认证状态"""
    verification = db.query(IdentityVerificationModel).filter(
        IdentityVerificationModel.user_id == current_user.id
    ).first()
    
    if not verification:
        return {
            "success": False,
            "message": "未提交实名认证信息",
            "data": None
        }
    
    # 解密身份证号以获取最后4位
    try:
        decrypted_id_card = decrypt_sensitive_data(getattr(verification, 'id_card_number', ''))
        last_four_digits = decrypted_id_card[-4:]
    except:
        last_four_digits = "****"
    
    # 构建响应对象
    verification_response = IdentityVerificationResponse(
        id=int(getattr(verification, 'id', 0)),
        user_id=int(getattr(verification, 'user_id', 0)),
        real_name=str(getattr(verification, 'real_name', '')),
        id_card_number="****" + last_four_digits,  # 只显示最后4位
        status=str(getattr(verification, 'status', '')),
        verification_time=getattr(verification, 'verification_time', None),
        created_at=getattr(verification, 'created_at', datetime.now()),
        updated_at=getattr(verification, 'updated_at', datetime.now())
    )
    
    return {
        "success": True,
        "message": "获取认证状态成功",
        "data": verification_response
    }