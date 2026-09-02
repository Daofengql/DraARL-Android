import gzip
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
DATASETS = (
    (
        ROOT / "app/src/main/res/raw/cqzones_json.gz",
        ROOT / "app/src/main/res/drawable-nodpi/cq_zone_reference.png",
        "cq_zone_number",
        "cq_zone_name_loc",
        "CQ zones 1-40",
    ),
    (
        ROOT / "app/src/main/res/raw/ituzones_json.gz",
        ROOT / "app/src/main/res/drawable-nodpi/itu_zone_reference.png",
        "itu_zone_number",
        "itu_zone_name_loc",
        "ITU zones 1-90",
    ),
)
WIDTH = 3072
HEIGHT = 1536
MARGIN = 48
MAP_TOP = 72
MAP_BOTTOM = HEIGHT - 72
MAP_HEIGHT = MAP_BOTTOM - MAP_TOP
MAP_WIDTH = WIDTH - 2 * MARGIN
PALETTE = (
    "#D9EAF4",
    "#F4E1A6",
    "#CDE7D1",
    "#E7D5EB",
    "#F2D2C4",
    "#D4E2C2",
    "#D8D9EF",
    "#F0DFCC",
)


def project(longitude: float, latitude: float) -> tuple[float, float]:
    x = (longitude + 180.0) / 360.0 * MAP_WIDTH
    y = (90.0 - latitude) / 180.0 * MAP_HEIGHT
    return x, y


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    names = ["arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold else ["arial.ttf", "DejaVuSans.ttf"]
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def generate(source: Path, output: Path, zone_property: str, label_property: str, caption: str) -> None:
    with gzip.open(source, "rt", encoding="utf-8") as source_file:
        features = json.load(source_file)["features"]

    image = Image.new("RGB", (WIDTH, HEIGHT), "#F6F8F7")
    draw = ImageDraw.Draw(image)
    map_image = Image.new("RGB", (MAP_WIDTH, MAP_HEIGHT), "#EEF2F0")
    map_draw = ImageDraw.Draw(map_image)

    for longitude in range(-180, 181, 30):
        x, _ = project(longitude, 0.0)
        map_draw.line((x, 0, x, MAP_HEIGHT), fill="#CED5D2", width=2)
    for latitude in range(-75, 76, 15):
        _, y = project(0.0, latitude)
        map_draw.line((0, y, MAP_WIDTH, y), fill="#CED5D2", width=2)

    for feature in features:
        zone = int(feature["properties"][zone_property])
        fill = PALETTE[(zone - 1) % len(PALETTE)]
        for ring in feature["geometry"]["coordinates"]:
            for longitude_shift in (-360.0, 0.0, 360.0):
                points = [project(float(point[0]) + longitude_shift, float(point[1])) for point in ring]
                map_draw.polygon(points, fill=fill, outline="#65716C", width=3)

    label_font = font(27, bold=True)
    for feature in features:
        properties = feature["properties"]
        zone = int(properties[zone_property])
        latitude, longitude = map(float, properties[label_property])
        while longitude < -180.0:
            longitude += 360.0
        while longitude > 180.0:
            longitude -= 360.0
        x, y = project(longitude, latitude)
        text = str(zone)
        bounds = map_draw.textbbox((0, 0), text, font=label_font, stroke_width=2)
        text_width = bounds[2] - bounds[0]
        text_height = bounds[3] - bounds[1]
        x = min(max(x, text_width / 2 + 8), MAP_WIDTH - text_width / 2 - 8)
        y = min(max(y, text_height / 2 + 8), MAP_HEIGHT - text_height / 2 - 8)
        map_draw.rounded_rectangle(
            (x - text_width / 2 - 5, y - text_height / 2 - 4, x + text_width / 2 + 5, y + text_height / 2 + 4),
            radius=5,
            fill="#FFFFFFD9",
        )
        map_draw.text(
            (x - text_width / 2, y - text_height / 2 - bounds[1]),
            text,
            font=label_font,
            fill="#17201D",
            stroke_width=1,
            stroke_fill="#FFFFFF",
        )

    image.paste(map_image, (MARGIN, MAP_TOP))
    draw.rectangle((MARGIN, MAP_TOP, WIDTH - MARGIN, MAP_BOTTOM), outline="#46514D", width=4)
    draw.text(
        (MARGIN, HEIGHT - 52),
        f"{caption} | Offline reference generated from HB9HIL hamradio-zones-geojson",
        font=font(24),
        fill="#46514D",
    )

    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output, optimize=True)
    print(f"Generated {output} ({output.stat().st_size} bytes)")


for dataset in DATASETS:
    generate(*dataset)
