
from flask import Flask, request, send_file
from PIL import Image, ImageDraw, ImageFont
import io

app = Flask(__name__)

@app.route('/')
def generate_image():
    width = int(request.args.get('width', 640))
    height = int(request.args.get('height', 360))
    bgcolor = request.args.get('bgcolor', '1976D2')
    textcolor = request.args.get('textcolor', 'FFFFFF')
    text = request.args.get('text', f'{width}x{height}')

    img = Image.new('RGB', (width, height), color=f'#{bgcolor}')
    d = ImageDraw.Draw(img)
    
    try:
        font = ImageFont.truetype("arial.ttf", 40)
    except IOError:
        font = ImageFont.load_default()

    d.text((width/2, height/2), text, fill=f'#{textcolor}', font=font, anchor="mm")

    img_io = io.BytesIO()
    img.save(img_io, 'PNG')
    img_io.seek(0)
    return send_file(img_io, mimetype='image/png')

if __name__ == '__main__':
    app.run(debug=True, port=5001)
