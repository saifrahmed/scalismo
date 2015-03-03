/*
 * Copyright 2015 University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scalismo.utils

import scalismo.image.{ DiscreteImageDomain, DiscreteScalarImage }
import scalismo.geometry._
import scalismo.mesh.{ TriangleCell, ScalarMeshData, TriangleMesh }
import vtk._
import reflect.runtime.universe.{ TypeTag, typeOf }
import scala.reflect.ClassTag
import scala.util.{ Success, Try, Failure }

import spire.math.Numeric

object VTKHelpers {
  val VTK_CHAR = 2
  val VTK_SIGNED_CHAR = 15
  val VTK_UNSIGNED_CHAR = 3
  val VTK_SHORT = 4
  val VTK_UNSIGNED_SHORT = 5
  val VTK_INT = 6
  val VTK_UNSIGNED_INT = 7
  val VTK_LONG = 8
  val VTK_UNSIGNED_LONG = 9
  val VTK_FLOAT = 10
  val VTK_DOUBLE = 11
  val VTK_ID_TYPE = 12

  def getVtkScalarType[Pixel: TypeTag]: Int = {
    typeOf[Pixel] match {
      case t if t =:= typeOf[Byte] => VTK_CHAR
      case t if t =:= typeOf[Short] => VTK_SHORT
      case t if t =:= typeOf[Int] => VTK_INT
      case t if t =:= typeOf[Long] => VTK_LONG
      case t if t =:= typeOf[Float] => VTK_FLOAT
      case t if t =:= typeOf[Double] => VTK_DOUBLE
      case _ => throw new NotImplementedError("Invalid scalar Pixel Type " + typeOf[Pixel])
    }
  }
  def createVtkDataArray[A: TypeTag](data: Array[A], numComp: Int) = {
    typeOf[A] match {
      case t if t =:= typeOf[Byte] =>
        val a = new vtkCharArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Char]])
        a
      case t if t =:= typeOf[Short] =>
        new vtkShortArray()
        val a = new vtkShortArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Short]])
        a
      case t if t =:= typeOf[Int] =>
        new vtkIntArray()
        val a = new vtkIntArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Int]])
        a
      case t if t =:= typeOf[Long] =>
        val a = new vtkLongArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Long]])
        a
      case t if t =:= typeOf[Float] =>
        val a = new vtkFloatArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Float]])
        a
      case t if t =:= typeOf[Double] =>
        val a = new vtkDoubleArray()
        a.SetNumberOfComponents(numComp)
        a.SetNumberOfTuples(data.size / numComp)
        a.SetJavaArray(data.asInstanceOf[Array[Double]])
        a
      case _ => throw new NotImplementedError("Invalid scalar Pixel Type " + typeOf[A])
    }
  }

  def getVTKArrayAsJavaArray[A: TypeTag](vtkType: Int, arrayVTK: vtkDataArray): Try[Array[A]] = {
    vtkType match {
      case VTK_CHAR =>
        Try {
          arrayVTK.asInstanceOf[vtkCharArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_UNSIGNED_CHAR =>
        Try {
          arrayVTK.asInstanceOf[vtkUnsignedCharArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_SHORT =>
        Try {
          arrayVTK.asInstanceOf[vtkShortArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_UNSIGNED_SHORT =>
        Try {
          arrayVTK.asInstanceOf[vtkUnsignedShortArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_INT =>
        Try {
          arrayVTK.asInstanceOf[vtkIntArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_UNSIGNED_INT =>
        Try {
          arrayVTK.asInstanceOf[vtkUnsignedIntArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_FLOAT =>
        Try {
          arrayVTK.asInstanceOf[vtkFloatArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case VTK_DOUBLE =>
        Try {
          arrayVTK.asInstanceOf[vtkDoubleArray].GetJavaArray().asInstanceOf[Array[A]]
        }
      case _ => throw new NotImplementedError("Invalid scalar Pixel Type " + typeOf[A])
    }
  }
}

object MeshConversion {

  private def vtkPolyDataToTriangleMeshCommon(pd: vtkPolyData, correctFlag: Boolean = false) = {

    val newPd = if (correctFlag) {
      val triangleFilter = new vtkTriangleFilter
      triangleFilter.SetInputData(pd)
      triangleFilter.Update()
      triangleFilter.GetOutput()
    } else pd

    val polys = newPd.GetPolys()
    val numPolys = polys.GetNumberOfCells()

    val pointsArrayVtk = newPd.GetPoints().GetData().asInstanceOf[vtkFloatArray]
    val pointsArray = pointsArrayVtk.GetJavaArray()
    val points = pointsArray.grouped(3).map(p => Point(p(0), p(1), p(2)))

    Try {
      val idList = new vtkIdList()
      val cells = for (i <- 0 until numPolys) yield {
        newPd.GetCellPoints(i, idList)
        if (idList.GetNumberOfIds() != 3) {
          throw new Exception("Not a triangle mesh")
        }

        TriangleCell(idList.GetId(0), idList.GetId(1), idList.GetId(2))
      }
      idList.Delete()
      (points, cells)
    }
  }

  def vtkPolyDataToTriangleMesh(pd: vtkPolyData): Try[TriangleMesh] = {
    // TODO currently all data arrays are ignored    
    val cellsPointsOrFailure = vtkPolyDataToTriangleMeshCommon(pd)
    cellsPointsOrFailure.map {
      case (points, cells) =>
        TriangleMesh(points.toIndexedSeq, cells)
    }
  }

  def vtkPolyDataToCorrectedTriangleMesh(pd: vtkPolyData): Try[TriangleMesh] = {

    val cellsPointsOrFailure = vtkPolyDataToTriangleMeshCommon(pd, correctFlag = true)
    cellsPointsOrFailure.map {
      case (points, cells) =>
        val cellPointIds = cells.flatMap(_.pointIds).distinct
        val oldId2newId = cellPointIds.zipWithIndex.toMap
        val newCells = cells.map(c => TriangleCell(oldId2newId(c.ptId1), oldId2newId(c.ptId2), oldId2newId(c.ptId3)))
        val oldPoints = points.toIndexedSeq
        val newPoints = cellPointIds.map(oldPoints)
        TriangleMesh(newPoints, newCells)
    }
  }

  def meshToVTKPolyData(mesh: TriangleMesh, template: Option[vtkPolyData] = None): vtkPolyData = {

    val pd = new vtkPolyData

    template match {
      case Some(tpl) =>
        // copy triangles from template if given; actual points are set unconditionally in code below.
        pd.ShallowCopy(tpl)
      case None =>
        val triangles = new vtkCellArray
        triangles.SetNumberOfCells(mesh.cells.size)
        triangles.Initialize()
        for ((cell, cell_id) <- mesh.cells.zipWithIndex) {
          val triangle = new vtkTriangle()

          triangle.GetPointIds().SetId(0, cell.ptId1)
          triangle.GetPointIds().SetId(1, cell.ptId2)
          triangle.GetPointIds().SetId(2, cell.ptId3)
          triangles.InsertNextCell(triangle)
        }
        triangles.Squeeze()
        pd.SetPolys(triangles)
    }

    // set points
    val pointDataArray = mesh.points.toIndexedSeq.toArray.map(_.data).flatten
    val pointDataArrayVTK = VTKHelpers.createVtkDataArray(pointDataArray, 3)
    val pointsVTK = new vtkPoints
    pointsVTK.SetData(pointDataArrayVTK)
    pd.SetPoints(pointsVTK)

    pd
  }

  def meshDataToVtkPolyData[S: Numeric: ClassTag: TypeTag](meshData: ScalarMeshData[S]): vtkPolyData = {
    val pd = meshToVTKPolyData(meshData.mesh)
    val scalarData = VTKHelpers.createVtkDataArray(meshData.data, 1) // TODO make this more general
    pd.GetPointData().SetScalars(scalarData)
    pd
  }
}

object ImageConversion {

  def imageTovtkStructuredPoints[D <: Dim: CanConvertToVTK, Pixel: Numeric: ClassTag: TypeTag](img: DiscreteScalarImage[D, Pixel]): vtkStructuredPoints = {
    implicitly[CanConvertToVTK[D]].toVTK(img)
  }

  def vtkStructuredPointsToScalarImage[D <: Dim: CanConvertToVTK, Pixel: Numeric: TypeTag: ClassTag](sp: vtkImageData): Try[DiscreteScalarImage[D, Pixel]] = {
    implicitly[CanConvertToVTK[D]].fromVTK(sp)
  }

  //  def image3DToImageJImagePlus[Pixel: ScalarValue](img: DiscreteScalarImage[ThreeD, Pixel]) = {
  //    val pixelConv = implicitly[ScalarValue[Pixel]]
  //    val domain = img.domain
  //    val (width, height, size) = (domain.size(0), domain.size(1), domain.size(2))
  //
  //    // 	Create 3x3x3 3D stack and fill it with garbage  
  //    val stack = new ImageStack(width, height)
  //
  //    val pixelValues = img.pixelValues.map(pixelConv.toFloat(_))
  //    for (slice <- 0 until size) {
  //      val startInd = slice * (width * height)
  //      val endInd = (slice + 1) * (width * height)
  //      val pixelForSlice = pixelValues.slice(startInd, endInd).toArray
  //      val bp = new FloatProcessor(width, height, pixelForSlice)
  //      stack.addSlice(bp)
  //
  //    }
  //    new ImagePlus("3D image", stack)
  //  }
  //
  //  def image2DToImageJImagePlus[Pixel: ScalarValue](img: DiscreteScalarImage[TwoD, Pixel]) = {
  //    val pixelConv = implicitly[ScalarValue[Pixel]]
  //    val domain = img.domain
  //    val bp = new FloatProcessor(domain.size(0), domain.size(1), img.pixelValues.map(pixelConv.toFloat(_)).toArray)
  //    new ImagePlus("2D image", bp)
  //  }
}

trait CanConvertToVTK[D <: Dim] {
  def toVTK[Pixel: Numeric: ClassTag: TypeTag](img: DiscreteScalarImage[D, Pixel]): vtkStructuredPoints
  def fromVTK[Pixel: Numeric: TypeTag: ClassTag](sp: vtkImageData): Try[DiscreteScalarImage[D, Pixel]]
}

object CanConvertToVTK {
  implicit object _2DCanConvertToVTK extends CanConvertToVTK[_2D] {
    override def toVTK[Pixel: Numeric: ClassTag: TypeTag](img: DiscreteScalarImage[_2D, Pixel]): vtkStructuredPoints = {
      val sp = new vtkStructuredPoints()
      val domain = img.domain

      val info = new vtkInformation() // TODO check what to do with the info
      sp.SetNumberOfScalarComponents(1, info)
      sp.SetScalarType(VTKHelpers.getVtkScalarType[Pixel], info)

      val dataArray = VTKHelpers.createVtkDataArray(img.values.toArray, 1)
      sp.GetPointData().SetScalars(dataArray)

      sp.SetDimensions(domain.size(0), domain.size(1), 1)
      sp.SetOrigin(domain.origin(0), domain.origin(1), 0)
      sp.SetSpacing(domain.spacing(0), domain.spacing(1), 0)
      sp
    }

    override def fromVTK[Pixel: Numeric: TypeTag: ClassTag](sp: vtkImageData): Try[DiscreteScalarImage[_2D, Pixel]] = {
      if (sp.GetNumberOfScalarComponents() != 1) {
        return Failure(new Exception(s"The image is not a scalar image (number of components is ${sp.GetNumberOfScalarComponents()}"))
      }

      if (sp.GetDimensions()(2) != 1 && sp.GetDimensions()(1) != 0) {
        return Failure(new Exception(s"The image is a 3D image - require a 2D image"))
      }

      val requiredScalarType = VTKHelpers.getVtkScalarType[Pixel]
      val spScalarType = sp.GetScalarType()
      if (requiredScalarType != spScalarType) {
        return Failure(new Exception(s"Invalid scalar type ($requiredScalarType != $spScalarType)"))
      }

      val origin = Point(sp.GetOrigin()(0).toFloat, sp.GetOrigin()(1).toFloat)
      val spacing = Vector(sp.GetSpacing()(0).toFloat, sp.GetSpacing()(1).toFloat)
      val size = Index(sp.GetDimensions()(0), sp.GetDimensions()(1))

      val domain = DiscreteImageDomain[_2D](origin, spacing, size)
      val scalars = sp.GetPointData().GetScalars()
      val pixelArrayOrFailure = VTKHelpers.getVTKArrayAsJavaArray[Pixel](sp.GetScalarType(), scalars)
      pixelArrayOrFailure.map(pixelArray => DiscreteScalarImage(domain, pixelArray))

    }
  }

  implicit object _3DCanConvertToVTK extends CanConvertToVTK[_3D] {
    override def toVTK[Pixel: Numeric: ClassTag: TypeTag](img: DiscreteScalarImage[_3D, Pixel]): vtkStructuredPoints = {
      val sp = new vtkStructuredPoints()
      val domain = img.domain

      val info = new vtkInformation() // TODO check what to do with the info
      sp.SetNumberOfScalarComponents(1, info)
      sp.SetScalarType(VTKHelpers.getVtkScalarType[Pixel], info)

      val dataArray = VTKHelpers.createVtkDataArray(img.values.toArray, 1)
      sp.GetPointData().SetScalars(dataArray)

      sp.SetDimensions(domain.size(0), domain.size(1), domain.size(2))
      sp.SetOrigin(domain.origin(0), domain.origin(1), domain.origin(2))
      sp.SetSpacing(domain.spacing(0), domain.spacing(1), domain.spacing(2))
      sp
    }

    override def fromVTK[Pixel: Numeric: TypeTag: ClassTag](sp: vtkImageData): Try[DiscreteScalarImage[_3D, Pixel]] = {
      if (sp.GetNumberOfScalarComponents() != 1) {
        return Failure(new Exception(s"The image is not a scalar image (number of components is ${sp.GetNumberOfScalarComponents()}"))
      }

      if (sp.GetDimensions()(2) == 1 || sp.GetDimensions()(2) == 0) {
        return Failure(new Exception(s"The image is a 2D image - require a 3D image"))
      }

      val requiredScalarType = VTKHelpers.getVtkScalarType[Pixel]
      val spScalarType = sp.GetScalarType()
      if (requiredScalarType != spScalarType) {
        return Failure(new Exception(s"Invalid scalar type ($requiredScalarType != $spScalarType)"))
      }

      val origin = Point(sp.GetOrigin()(0).toFloat, sp.GetOrigin()(1).toFloat, sp.GetOrigin()(2).toFloat)
      val spacing = Vector(sp.GetSpacing()(0).toFloat, sp.GetSpacing()(1).toFloat, sp.GetSpacing()(2).toFloat)
      val size = Index(sp.GetDimensions()(0), sp.GetDimensions()(1), sp.GetDimensions()(2))

      val domain = DiscreteImageDomain[_3D](origin, spacing, size)
      val scalars = sp.GetPointData().GetScalars()
      val pixelArrayOrFailure = VTKHelpers.getVTKArrayAsJavaArray[Pixel](sp.GetScalarType(), scalars)
      pixelArrayOrFailure.map(pixelArray => DiscreteScalarImage(domain, pixelArray))
    }

  }
}
